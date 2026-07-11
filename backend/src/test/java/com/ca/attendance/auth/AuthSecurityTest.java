package com.ca.attendance.auth;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.user.UserRepository;
import com.ca.attendance.user.UserSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

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

        AuthInterceptor interceptor = new AuthInterceptor(tokens, users);
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

        AuthInterceptor interceptor = new AuthInterceptor(tokens, users);
        interceptor.preHandle(request("/api/attendance/me", token), new MockHttpServletResponse(), new Object());

        assertThat(AuthContext.current().role()).isEqualTo(Role.MEMBER);
    }

    @Test
    void interceptorAllowsOnlyPasswordAndSessionEndpointsUntilPasswordChanges() {
        TokenService tokens = new TokenService(12);
        String token = tokens.issue(1L, "member", "成员", Role.MEMBER);
        when(users.findSummaryById(1L)).thenReturn(Optional.of(user(Role.MEMBER, "ACTIVE", true)));
        AuthInterceptor interceptor = new AuthInterceptor(tokens, users);

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
        AuthService service = new AuthService(users, jdbc, passwords, tokenService);

        service.changePassword("old-password", "new-password");

        verify(tokenService).revokeUser(1L);
    }

    private MockHttpServletRequest request(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private UserSummary user(Role role, String status, boolean mustChangePassword) {
        LocalDateTime now = LocalDateTime.now();
        return new UserSummary(1L, "admin", "管理员", role, status,
                null, null, null, null, mustChangePassword, now, now);
    }
}
