package com.ca.attendance.auth;

import com.ca.attendance.access.RemoteAccessPolicy;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.user.UserRepository;
import com.ca.attendance.user.UserSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthSecurityTest {
    @Mock
    private UserRepository users;
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private PasswordEncoder passwords;
    @Mock
    private TokenService tokenService;
    @Mock
    private OperationLogService logs;

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void revokeUserInvalidatesEverySessionForThatUserOnly() {
        TokenService tokens = new TokenService(12);
        String first = tokens.issue(1L, "admin", "管理员", Role.ADMIN);
        String second = tokens.issue(1L, "admin", "管理员", Role.ADMIN);
        String other = tokens.issue(2L, "member", "成员", Role.MEMBER);

        tokens.revokeUser(1L);

        assertThatThrownBy(() -> tokens.require(first)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> tokens.require(second)).isInstanceOf(ApiException.class);
        assertThat(tokens.require(other).id()).isEqualTo(2L);
    }

    @Test
    void interceptorRejectsDisabledAccountEvenWhenItsTokenHasNotExpired() {
        TokenService tokens = new TokenService(12);
        String token = tokens.issue(1L, "admin", "管理员", Role.ADMIN);
        when(users.findSummaryById(1L)).thenReturn(Optional.of(user(Role.ADMIN, "DISABLED", false)));

        AuthInterceptor interceptor = new AuthInterceptor(tokens, users, new RemoteAccessPolicy(8081));
        MockHttpServletRequest request = request("/api/users", token);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("停用");
        assertThatThrownBy(() -> tokens.require(token)).isInstanceOf(ApiException.class);
    }

    @Test
    void interceptorUsesCurrentRoleInsteadOfRoleCapturedAtLogin() {
        TokenService tokens = new TokenService(12);
        String token = tokens.issue(1L, "admin", "管理员", Role.ADMIN);
        when(users.findSummaryById(1L)).thenReturn(Optional.of(user(Role.MEMBER, "ACTIVE", false)));

        AuthInterceptor interceptor = new AuthInterceptor(tokens, users, new RemoteAccessPolicy(8081));
        interceptor.preHandle(request("/api/attendance/me", token), new MockHttpServletResponse(), new Object());

        assertThat(AuthContext.current().role()).isEqualTo(Role.MEMBER);
    }

    @Test
    void interceptorAllowsOnlyPasswordAndSessionEndpointsUntilPasswordChanges() {
        TokenService tokens = new TokenService(12);
        String token = tokens.issue(1L, "member", "成员", Role.MEMBER);
        when(users.findSummaryById(1L)).thenReturn(Optional.of(user(Role.MEMBER, "ACTIVE", true)));
        AuthInterceptor interceptor = new AuthInterceptor(tokens, users, new RemoteAccessPolicy(8081));

        assertThatThrownBy(() -> interceptor.preHandle(
                request("/api/attendance/me", token), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("修改初始密码");

        assertThat(interceptor.preHandle(
                request("/api/auth/change-password", token), new MockHttpServletResponse(), new Object()))
                .isTrue();
    }

    @Test
    void changingPasswordRevokesEveryExistingSessionForTheUser() {
        AuthContext.set(new AuthUser(1L, "admin", "管理员", Role.ADMIN,
                java.time.Instant.now().plusSeconds(3600)));
        when(users.findLoginByStudentNo("admin")).thenReturn(Optional.of(
                new UserRepository.UserLoginRow(
                        1L, "admin", "管理员", "old-hash", Role.ADMIN, "ACTIVE", true
                )
        ));
        when(passwords.matches("old-password", "old-hash")).thenReturn(true);
        when(passwords.encode("new-password")).thenReturn("new-hash");
        when(jdbc.update(anyString(), eq("new-hash"), eq(1L), eq(1L))).thenReturn(1);
        AuthService service = new AuthService(
                users, jdbc, passwords, tokenService,
                new RemoteAccessPolicy(8081), new LoginAttemptGuard(),
                new AuthenticationEventService(jdbc, logs)
        );

        service.changePassword("old-password", "new-password");

        verify(tokenService).revokeUser(1L);
    }

    @Test
    void changingPasswordRejectsAnInvalidNewPasswordBeforeUpdatingTheAccount() {
        AuthContext.set(new AuthUser(1L, "legacy-admin", "管理员", Role.ADMIN,
                java.time.Instant.now().plusSeconds(3600)));
        when(users.findLoginByStudentNo("legacy-admin")).thenReturn(Optional.of(
                new UserRepository.UserLoginRow(
                        1L, "legacy-admin", "管理员", "old-hash", Role.ADMIN, "ACTIVE", false
                )
        ));
        when(passwords.matches("old-password", "old-hash")).thenReturn(true);
        AuthService service = new AuthService(
                users, jdbc, passwords, tokenService,
                new RemoteAccessPolicy(8081), new LoginAttemptGuard(),
                new AuthenticationEventService(jdbc, logs)
        );

        assertThatThrownBy(() -> service.changePassword("old-password", "12345"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("6 至 64");

        verifyNoInteractions(jdbc);
        verify(tokenService, never()).revokeUser(1L);
    }

    @Test
    void remoteLoginAllowsOnlyPresidentAndAdministrator() {
        when(users.findLoginByStudentNo("minister")).thenReturn(Optional.of(
                new UserRepository.UserLoginRow(
                        2L, "minister", "测试部长", "hash", Role.MINISTER, "ACTIVE", false
                )
        ));
        when(passwords.matches("correct-password", "hash")).thenReturn(true);
        AuthService service = new AuthService(
                users, jdbc, passwords, tokenService,
                new RemoteAccessPolicy(8081), new LoginAttemptGuard(),
                new AuthenticationEventService(jdbc, logs)
        );

        assertThatThrownBy(() -> service.login(
                "minister", "correct-password",
                new AuthService.LoginContext(true, "203.0.113.10", "test-agent")
        )).isInstanceOf(ApiException.class).hasMessageContaining("会长或管理员");

        verify(tokenService, never()).issue(2L, "minister", "测试部长", Role.MINISTER);
    }

    @Test
    void remotePresidentLoginIssuesTokenAndWritesSecurityAudit() {
        when(users.findLoginByStudentNo("president")).thenReturn(Optional.of(
                new UserRepository.UserLoginRow(
                        3L, "president", "测试会长", "hash", Role.PRESIDENT, "ACTIVE", false
                )
        ));
        when(passwords.matches("correct-password", "hash")).thenReturn(true);
        when(tokenService.issue(3L, "president", "测试会长", Role.PRESIDENT)).thenReturn("remote-token");
        when(jdbc.update(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(3L)
        )).thenReturn(1);
        AuthService service = new AuthService(
                users, jdbc, passwords, tokenService,
                new RemoteAccessPolicy(8081), new LoginAttemptGuard(),
                new AuthenticationEventService(jdbc, logs)
        );
        AuthService.LoginContext context = new AuthService.LoginContext(true, "203.0.113.12", "test-agent");

        AuthService.LoginResponse response = service.login("president", "correct-password", context);

        assertThat(response.token()).isEqualTo("remote-token");
        assertThat(response.role()).isEqualTo("PRESIDENT");
        verify(logs).logAuthentication(
                OperationLogService.AuthenticationOutcome.SUCCESS,
                true,
                users.findLoginByStudentNo("president").orElseThrow(),
                "president",
                "203.0.113.12",
                "test-agent",
                "远程后台登录成功"
        );
    }

    @Test
    void localMinisterLoginKeepsExistingAccess() {
        when(users.findLoginByStudentNo("minister")).thenReturn(Optional.of(
                new UserRepository.UserLoginRow(
                        2L, "minister", "测试部长", "hash", Role.MINISTER, "ACTIVE", false
                )
        ));
        when(passwords.matches("correct-password", "hash")).thenReturn(true);
        when(tokenService.issue(2L, "minister", "测试部长", Role.MINISTER)).thenReturn("local-token");
        when(jdbc.update(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(2L)
        )).thenReturn(1);
        AuthService service = new AuthService(
                users, jdbc, passwords, tokenService,
                new RemoteAccessPolicy(8081), new LoginAttemptGuard(),
                new AuthenticationEventService(jdbc, logs)
        );

        AuthService.LoginResponse response = service.login("minister", "correct-password");

        assertThat(response.token()).isEqualTo("local-token");
        assertThat(response.role()).isEqualTo("MINISTER");
        verify(logs).logAuthentication(
                OperationLogService.AuthenticationOutcome.SUCCESS,
                false,
                users.findLoginByStudentNo("minister").orElseThrow(),
                "minister",
                "127.0.0.1",
                "local",
                "本机后台登录成功"
        );
    }

    @Test
    void nonexistentAccountStillPerformsAnEqualCostPasswordCheck() {
        AuthService service = new AuthService(
                users, jdbc, passwords, tokenService,
                new RemoteAccessPolicy(8081), new LoginAttemptGuard(),
                new AuthenticationEventService(jdbc, logs)
        );

        assertThatThrownBy(() -> service.login("missing", "wrong-password"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("学号或密码错误");

        ArgumentCaptor<String> dummyHash = ArgumentCaptor.forClass(String.class);
        verify(passwords).matches(org.mockito.ArgumentMatchers.eq("wrong-password"), dummyHash.capture());
        assertThat(dummyHash.getValue()).startsWith("$2");
        verify(logs).logAuthentication(
                OperationLogService.AuthenticationOutcome.FAILURE,
                false,
                null,
                "missing",
                "127.0.0.1",
                "local",
                "学号或密码错误"
        );
    }

    @Test
    void localLoginIsNotRateLimited() {
        AuthService service = new AuthService(
                users, jdbc, passwords, tokenService,
                new RemoteAccessPolicy(8081), new LoginAttemptGuard(),
                new AuthenticationEventService(jdbc, logs)
        );

        for (int attempt = 0; attempt < 20; attempt++) {
            assertThatThrownBy(() -> service.login("unknown", "wrong-password"))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("学号或密码错误");
        }
    }

    @Test
    void remoteRequestsRejectKioskAndNonPrivilegedSessions() {
        TokenService tokens = new TokenService(12);
        AuthInterceptor interceptor = new AuthInterceptor(tokens, users, new RemoteAccessPolicy(8081));

        MockHttpServletRequest appearanceRequest = request("/api/public/appearance", "");
        appearanceRequest.setLocalPort(8081);
        assertThat(interceptor.preHandle(
                appearanceRequest, new MockHttpServletResponse(), new Object())).isTrue();

        MockHttpServletRequest kioskRequest = request("/api/public/attendance/lookup", "");
        kioskRequest.setLocalPort(8081);
        assertThatThrownBy(() -> interceptor.preHandle(kioskRequest, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("签到台仅限主机本地使用");

        String ministerToken = tokens.issue(2L, "minister", "测试部长", Role.MINISTER);
        when(users.findSummaryById(2L)).thenReturn(Optional.of(user(2L, Role.MINISTER, "ACTIVE", false)));
        MockHttpServletRequest adminApiRequest = request("/api/stats/summary", ministerToken);
        adminApiRequest.setLocalPort(8081);
        assertThatThrownBy(() -> interceptor.preHandle(adminApiRequest, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("会长或管理员");
    }

    @Test
    void remoteLoginIsRateLimitedAfterRepeatedFailures() {
        AuthService service = new AuthService(
                users, jdbc, passwords, tokenService,
                new RemoteAccessPolicy(8081), new LoginAttemptGuard(),
                new AuthenticationEventService(jdbc, logs)
        );
        AuthService.LoginContext context = new AuthService.LoginContext(true, "203.0.113.11", "test-agent");

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.login("unknown", "wrong-password", context))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("学号或密码错误");
        }
        assertThatThrownBy(() -> service.login("unknown", "wrong-password", context))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("尝试次数过多");
    }

    @Test
    void rotatingRemoteAccountsCreateOneGlobalLockAuditEvent() {
        AuthService service = new AuthService(
                users, jdbc, passwords, tokenService,
                new RemoteAccessPolicy(8081), new LoginAttemptGuard(),
                new AuthenticationEventService(jdbc, logs)
        );
        AuthService.LoginContext context = new AuthService.LoginContext(true, "127.0.0.1", "test-agent");

        for (int attempt = 0; attempt < LoginAttemptGuard.REMOTE_GLOBAL_MAX_FAILURES; attempt++) {
            String account = "unknown-" + attempt;
            assertThatThrownBy(() -> service.login(account, "wrong-password", context))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("学号或密码错误");
        }
        assertThatThrownBy(() -> service.login("another-account", "wrong-password", context))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("远程登录");

        verify(logs, times(1)).logAuthentication(
                OperationLogService.AuthenticationOutcome.LOCKED,
                true,
                null,
                "unknown-29",
                "127.0.0.1",
                "test-agent",
                "学号或密码错误"
        );
    }

    @Test
    void remoteLoginContextIgnoresUntrustedForwardedAddress() {
        RemoteAccessPolicy policy = new RemoteAccessPolicy(8081);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setLocalPort(8081);
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.200");

        AuthService.LoginContext context = policy.loginContext(request);

        assertThat(context.remote()).isTrue();
        assertThat(context.clientAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void spoofedForwardedAddressesCannotBypassRemoteLoginLimit() {
        RemoteAccessPolicy policy = new RemoteAccessPolicy(8081);
        LoginAttemptGuard guard = new LoginAttemptGuard();

        for (int attempt = 0; attempt < 5; attempt++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setLocalPort(8081);
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", "203.0.113." + attempt);
            AuthService.LoginContext context = policy.loginContext(request);
            guard.requireAllowed("admin", context);
            guard.recordFailure("admin", context);
        }

        MockHttpServletRequest nextRequest = new MockHttpServletRequest("POST", "/api/auth/login");
        nextRequest.setLocalPort(8081);
        nextRequest.setRemoteAddr("127.0.0.1");
        nextRequest.addHeader("X-Forwarded-For", "198.51.100.99");

        assertThatThrownBy(() -> guard.requireAllowed("admin", policy.loginContext(nextRequest)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("尝试次数过多");
    }

    private MockHttpServletRequest request(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private UserSummary user(Role role, String status, boolean mustChangePassword) {
        return user(1L, role, status, mustChangePassword);
    }

    private UserSummary user(long id, Role role, String status, boolean mustChangePassword) {
        LocalDateTime now = LocalDateTime.now();
        return new UserSummary(id, "admin", "管理员", role, status,
                null, null, null, null, mustChangePassword, now, now);
    }
}
