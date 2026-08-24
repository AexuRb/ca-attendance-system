package com.ca.attendance.access;

import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.remote.port=0")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoleAuthorizationWebIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TokenService tokens;

    private final Map<Role, String> roleTokens = new EnumMap<>(Role.class);
    private MockMvc mvc;
    private long trainingSessionId;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("DELETE FROM users WHERE student_no LIKE 'authz-%'");
        roleTokens.clear();
        for (Role role : Role.values()) {
            String studentNo = "authz-" + role.name().toLowerCase();
            Long id = jdbc.queryForObject("""
                    INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                    VALUES (?, ?, 'test-hash', ?, 'ACTIVE', 0)
                    RETURNING id
                    """, Long.class, studentNo, role.name() + "测试账号", role.name());
            if (id == null) {
                throw new IllegalStateException("权限测试账号创建失败");
            }
            roleTokens.put(role, tokens.issue(id, studentNo, role.name() + "测试账号", role));
        }
        Long createdSessionId = jdbc.queryForObject("""
                INSERT INTO training_sessions (title, training_date, status)
                VALUES ('authz-training', '2026-08-05', 'COMPLETED')
                RETURNING id
                """, Long.class);
        trainingSessionId = createdSessionId == null ? 0 : createdSessionId;
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM training_sessions WHERE id = ?", trainingSessionId);
        jdbc.update("DELETE FROM users WHERE student_no LIKE 'authz-%'");
    }

    @Test
    void memberCanReadOnlyPersonalBusinessData() throws Exception {
        authenticatedGet(Role.MEMBER, "/api/attendance/me?from=2026-08-01&to=2026-08-10")
                .andExpect(status().isOk());
        authenticatedGet(Role.MEMBER, "/api/trainings/me?from=2026-08-01&to=2026-08-10")
                .andExpect(status().isOk());

        for (String path : new String[]{
                "/api/attendance/page?from=2026-08-01&to=2026-08-10",
                "/api/stats/summary?from=2026-08-01&to=2026-08-10",
                "/api/users/page",
                "/api/schedules",
                "/api/trainings/page",
                "/api/repairs?from=2026-08-01&to=2026-08-10",
                "/api/maintenance/summary",
                "/api/settings/attendance-policy",
                "/api/logs"
        }) {
            authenticatedGet(Role.MEMBER, path).andExpect(status().isForbidden());
        }
        authenticatedGet(Role.MEMBER, trainingParticipantPagePath()).andExpect(status().isForbidden());
    }

    @Test
    void ministerReceivesOperationalAccessWithoutPrivateManagementAccess() throws Exception {
        for (String path : new String[]{
                "/api/attendance/page?from=2026-08-01&to=2026-08-10",
                "/api/stats/summary?from=2026-08-01&to=2026-08-10",
                "/api/repairs?from=2026-08-01&to=2026-08-10"
        }) {
            authenticatedGet(Role.MINISTER, path).andExpect(status().isOk());
        }

        for (String path : new String[]{
                "/api/users/page",
                "/api/schedules",
                "/api/trainings/page",
                "/api/maintenance/summary",
                "/api/settings/attendance-policy",
                "/api/exports/options",
                "/api/logs"
        }) {
            authenticatedGet(Role.MINISTER, path).andExpect(status().isForbidden());
        }
        authenticatedGet(Role.MINISTER, trainingParticipantPagePath()).andExpect(status().isForbidden());
    }

    @Test
    void presidentManagesAssociationDataButNotAdministratorOnlyOperations() throws Exception {
        for (String path : new String[]{
                "/api/users/page",
                "/api/schedules",
                "/api/trainings/page",
                "/api/repairs?from=2026-08-01&to=2026-08-10",
                "/api/maintenance/summary",
                "/api/maintenance/backups",
                "/api/settings/attendance-policy",
                "/api/exports/options"
        }) {
            authenticatedGet(Role.PRESIDENT, path).andExpect(status().isOk());
        }
        authenticatedGet(Role.PRESIDENT, trainingParticipantPagePath()).andExpect(status().isOk());

        authenticatedGet(Role.PRESIDENT, "/api/logs").andExpect(status().isForbidden());
        authenticatedGet(Role.PRESIDENT, "/api/repairs/recycle-bin").andExpect(status().isForbidden());
        authenticatedDelete(Role.PRESIDENT, "/api/users/999999").andExpect(status().isForbidden());
        authenticatedDelete(Role.PRESIDENT, "/api/maintenance/backups/missing.zip")
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanReachAdministratorOnlyInterfaces() throws Exception {
        for (String path : new String[]{
                "/api/users/page",
                "/api/schedules",
                "/api/trainings/page",
                "/api/maintenance/summary",
                "/api/maintenance/backups",
                "/api/settings/attendance-policy",
                "/api/exports/options",
                "/api/repairs/recycle-bin",
                "/api/logs"
        }) {
            authenticatedGet(Role.ADMIN, path).andExpect(status().isOk());
        }
        authenticatedGet(Role.ADMIN, trainingParticipantPagePath()).andExpect(status().isOk());
    }

    @Test
    void directApiRejectsUnknownEnumFiltersAndSelfServiceGradeChanges() throws Exception {
        authenticatedGet(Role.ADMIN, "/api/logs?actionType=bulk_review_attendance")
                .andExpect(status().isOk());
        authenticatedGet(Role.ADMIN, "/api/logs/export?actionType=BULK_REVIEW_ATTENDANCE")
                .andExpect(status().isOk());

        for (String path : new String[]{
                "/api/users/page?role=%25",
                "/api/users/page?status=%25",
                "/api/attendance/page?from=2026-08-01&to=2026-08-10&status=%25",
                "/api/trainings/page?status=%25",
                "/api/repairs?from=2026-08-01&to=2026-08-10&status=%25",
                "/api/logs?actionType=NOT_EXIST"
        }) {
            authenticatedGet(Role.ADMIN, path).andExpect(status().isBadRequest());
        }

        mvc.perform(put("/api/me/profile")
                        .header("Authorization", "Bearer " + roleTokens.get(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800000000\",\"major\":\"测试学院\",\"grade\":\"2099级\",\"qq\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("年级只能由会长或管理员在成员管理中修改"));
    }

    @Test
    void percentAndUnderscoreAreLiteralInMemberKeywordSearch() throws Exception {
        jdbc.update("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('authz-literal-percent', '百分号%成员', 'test-hash', 'MEMBER', 'ACTIVE', 0),
                       ('authz-literal-underscore', '下划线_成员', 'test-hash', 'MEMBER', 'ACTIVE', 0),
                       ('authz-literal-backslash', '反斜线\\成员', 'test-hash', 'MEMBER', 'ACTIVE', 0),
                       ('authz-literal-ordinary', '普通成员', 'test-hash', 'MEMBER', 'ACTIVE', 0)
                """);

        mvc.perform(get("/api/users/page")
                        .header("Authorization", "Bearer " + roleTokens.get(Role.ADMIN))
                        .param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value("百分号%成员"));

        mvc.perform(get("/api/users/page")
                        .header("Authorization", "Bearer " + roleTokens.get(Role.ADMIN))
                        .param("keyword", "_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value("下划线_成员"));

        mvc.perform(get("/api/users/page")
                        .header("Authorization", "Bearer " + roleTokens.get(Role.ADMIN))
                        .param("keyword", "\\"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value("反斜线\\成员"));
    }

    @Test
    void unboundedTrainingReadEndpointsAreNotExposed() throws Exception {
        authenticatedGet(Role.ADMIN, "/api/trainings")
                .andExpect(status().isMethodNotAllowed());
        authenticatedGet(Role.ADMIN, "/api/trainings/" + trainingSessionId + "/participants")
                .andExpect(status().isMethodNotAllowed());
    }

    private ResultActions authenticatedGet(Role role, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + roleTokens.get(role)));
    }

    private ResultActions authenticatedDelete(Role role, String path) throws Exception {
        return mvc.perform(delete(path).header("Authorization", "Bearer " + roleTokens.get(role)));
    }

    private String trainingParticipantPagePath() {
        return "/api/trainings/" + trainingSessionId + "/participants/page";
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("ca-role-authorization-test-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
