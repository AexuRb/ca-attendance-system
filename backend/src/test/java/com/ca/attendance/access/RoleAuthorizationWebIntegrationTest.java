package com.ca.attendance.access;

import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
    }

    @AfterEach
    void tearDown() {
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
                "/api/trainings",
                "/api/repairs?from=2026-08-01&to=2026-08-10",
                "/api/maintenance/summary",
                "/api/settings/attendance-policy",
                "/api/logs"
        }) {
            authenticatedGet(Role.MEMBER, path).andExpect(status().isForbidden());
        }
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
                "/api/trainings",
                "/api/maintenance/summary",
                "/api/settings/attendance-policy",
                "/api/exports/options",
                "/api/logs"
        }) {
            authenticatedGet(Role.MINISTER, path).andExpect(status().isForbidden());
        }
    }

    @Test
    void presidentManagesAssociationDataButNotAdministratorOnlyOperations() throws Exception {
        for (String path : new String[]{
                "/api/users/page",
                "/api/schedules",
                "/api/trainings",
                "/api/repairs?from=2026-08-01&to=2026-08-10",
                "/api/maintenance/summary",
                "/api/maintenance/backups",
                "/api/settings/attendance-policy",
                "/api/exports/options"
        }) {
            authenticatedGet(Role.PRESIDENT, path).andExpect(status().isOk());
        }

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
                "/api/trainings",
                "/api/maintenance/summary",
                "/api/maintenance/backups",
                "/api/settings/attendance-policy",
                "/api/exports/options",
                "/api/repairs/recycle-bin",
                "/api/logs"
        }) {
            authenticatedGet(Role.ADMIN, path).andExpect(status().isOk());
        }
    }

    private ResultActions authenticatedGet(Role role, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + roleTokens.get(role)));
    }

    private ResultActions authenticatedDelete(Role role, String path) throws Exception {
        return mvc.perform(delete(path).header("Authorization", "Bearer " + roleTokens.get(role)));
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("ca-role-authorization-test-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
