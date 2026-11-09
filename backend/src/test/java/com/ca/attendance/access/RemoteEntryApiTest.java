package com.ca.attendance.access;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthInterceptor;
import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.GlobalExceptionHandler;
import com.ca.attendance.common.Role;
import com.ca.attendance.user.UserRepository;
import com.ca.attendance.user.UserSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RemoteEntryApiTest {
    private final UserRepository users = mock(UserRepository.class);
    private final TokenService tokens = new TokenService(12);
    private final ProbeController handler = new ProbeController();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(handler)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthInterceptor(tokens, users, new RemoteAccessPolicy(8081))).build();
    }

    @AfterEach
    void cleanup() { AuthContext.clear(); }

    @ParameterizedTest
    @CsvSource({
            "GET,/api/attendance/reviews/pending", "POST,/api/attendance/1/review",
            "POST,/api/attendance/reviews/bulk", "POST,/api/attendance/manual",
            "PUT,/api/attendance/1/manual", "DELETE,/api/attendance/1",
            "POST,/api/repairs", "PUT,/api/repairs/1", "DELETE,/api/repairs/1",
            "PATCH,/api/repairs/1", "POST,/api/repairs/1/restore", "POST,/api/repairs/1/purge",
            "GET,/api/repairs/handler-candidates", "GET,/api/attendance/manual-candidates",
            "GET,/api/repairs/export", "GET,/api/repairs/recycle-bin", "GET,/api/users",
            "GET,/api/logs", "GET,/api/maintenance/backups", "GET,/api/exports/options",
            "POST,/api/exports/preview", "POST,/api/future-business-operation"
    })
    void remoteMinisterCannotReachManagementHandlers(String method, String path) throws Exception {
        mvc.perform(call(method, path, token(Role.MINISTER), 8081)).andExpect(status().isForbidden());
        assertThat(handler.calls).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/api/attendance/page", "GET,/api/attendance", "GET,/api/stats/summary",
            "GET,/api/stats/weekly-detail", "GET,/api/stats/member-detail", "GET,/api/stats/dashboard",
            "GET,/api/stats/export", "GET,/api/repairs", "GET,/api/repairs/1/agreement",
            "HEAD,/api/repairs/1/agreement", "GET,/api/schedules", "GET,/api/public/schedules/today",
            "GET,/api/public/schedules/week", "GET,/api/public/duty-periods", "GET,/api/public/duty-weekdays"
    })
    void remoteMinisterRetainsApprovedReadAndExportEndpoints(String method, String path) throws Exception {
        mvc.perform(call(method, path, token(Role.MINISTER), 8081)).andExpect(status().isOk());
        assertThat(handler.calls).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/auth/me", "GET,/api/attendance/me", "GET,/api/trainings/me",
            "GET,/api/settings/credits", "PUT,/api/me/profile", "POST,/api/auth/change-password",
            "POST,/api/auth/logout"})
    void remoteMemberAndMinisterRetainPersonalAccountOperations(String method, String path) throws Exception {
        for (Role role : new Role[]{Role.MEMBER, Role.MINISTER})
            mvc.perform(call(method, path, token(role), 8081)).andExpect(status().isOk());
        assertThat(handler.calls).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/users", "GET,/api/attendance/page", "GET,/api/stats/summary",
            "GET,/api/trainings/page", "GET,/api/repairs", "GET,/api/schedules",
            "GET,/api/public/schedules/today", "GET,/api/maintenance/backups",
            "PUT,/api/users/1", "POST,/api/attendance/manual", "PUT,/api/settings/credits"})
    void remoteMemberCannotReadOrManageOtherMembers(String method, String path) throws Exception {
        mvc.perform(call(method, path, token(Role.MEMBER), 8081)).andExpect(status().isForbidden());
        assertThat(handler.calls).isZero();
    }

    @Test
    void theSameLocalTokenIsRestrictedAtTheRemoteConnectorUsingTheCurrentRole() throws Exception {
        String token = tokens.issue(1, "test-account", "测试账号", Role.ADMIN);
        currentRole(Role.MINISTER);
        mvc.perform(call("PUT", "/api/attendance/1/manual", token, 8080)).andExpect(status().isOk());
        mvc.perform(call("PUT", "/api/attendance/1/manual", token, 8081)
                .header("X-Forwarded-For", "127.0.0.1").header("X-Forwarded-Port", "8080"))
                .andExpect(status().isForbidden());
        assertThat(handler.calls).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRemoteRoleIsStillDeniedKioskSetupAndDesktopControl(Role role) throws Exception {
        String token = token(role);
        for (String path : new String[]{"/api/public/attendance/lookup", "/api/public/attendance/submit",
                "/api/setup/initialize", "/api/desktop/shutdown"})
            mvc.perform(call("POST", path, token, 8081)).andExpect(status().isForbidden());
        assertThat(handler.calls).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"PRESIDENT", "ADMIN"})
    void remoteLeadersRetainExistingBusinessEntryAccess(Role role) throws Exception {
        String token = token(role);
        mvc.perform(call("POST", "/api/attendance/1/review", token, 8081)).andExpect(status().isOk());
        mvc.perform(call("PUT", "/api/repairs/1", token, 8081)).andExpect(status().isOk());
    }

    @Test
    void accessContextAdvertisesAllRolesOnlyAtTheRemoteEntry() {
        RemoteAccessPolicy policy = new RemoteAccessPolicy(8081);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setLocalPort(8081);
        assertThat(policy.context(request).allowedRemoteRoles()).containsExactly("MEMBER", "MINISTER", "PRESIDENT", "ADMIN");
        assertThat(policy.context(request).kioskAvailable()).isFalse();
        request.setLocalPort(8080);
        assertThat(policy.context(request).allowedRemoteRoles()).isEmpty();
        assertThat(policy.context(request).kioskAvailable()).isTrue();
    }

    @Test
    void remoteMemberMustChangeTheInitialPasswordBeforeUsingPersonalRecords() throws Exception {
        String token = tokens.issue(1, "test-account", "测试账号", Role.MEMBER);
        LocalDateTime now = LocalDateTime.now();
        when(users.findSummaryById(1L)).thenReturn(Optional.of(new UserSummary(1, "test-account", "测试账号",
                Role.MEMBER, "ACTIVE", null, null, null, null, true, now, now)));
        mvc.perform(call("GET", "/api/attendance/me", token, 8081)).andExpect(status().isForbidden());
        mvc.perform(call("POST", "/api/auth/change-password", token, 8081)).andExpect(status().isOk());
        assertThat(handler.calls).isEqualTo(1);
    }

    private String token(Role role) { currentRole(role); return tokens.issue(1, "test-account", "测试账号", role); }
    private void currentRole(Role role) {
        LocalDateTime now = LocalDateTime.now();
        when(users.findSummaryById(1L)).thenReturn(Optional.of(new UserSummary(1, "test-account", "测试账号",
                role, "ACTIVE", null, null, null, null, false, now, now)));
    }
    private MockHttpServletRequestBuilder call(String method, String path, String token, int port) {
        return request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer " + token)
                .with(req -> { req.setLocalPort(port); return req; });
    }
    @RestController
    static class ProbeController {
        int calls;
        @RequestMapping("/api/**")
        Map<String, Long> handle() { calls++; return Map.of("userId", AuthContext.current().id()); }
    }
}
