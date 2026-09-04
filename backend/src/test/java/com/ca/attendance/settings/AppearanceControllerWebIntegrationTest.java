package com.ca.attendance.settings;

import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.remote.port=0")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AppearanceControllerWebIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TokenService tokens;

    private MockMvc mvc;
    private String adminToken;
    private String presidentToken;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("DELETE FROM operation_logs");
        jdbc.update("DELETE FROM users WHERE student_no LIKE 'appearance-web-%'");
        jdbc.update("""
                INSERT INTO app_settings (setting_key, setting_value, description)
                VALUES ('UI_APPEARANCE', 'CLASSIC', '全局界面外观')
                ON CONFLICT (setting_key) DO UPDATE SET setting_value = 'CLASSIC'
                """);
        adminToken = tokenFor("appearance-web-admin", "界面接口管理员", Role.ADMIN);
        presidentToken = tokenFor("appearance-web-president", "界面接口会长", Role.PRESIDENT);
    }

    @Test
    void publicEndpointReturnsOnlyAppearanceContractWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/public/appearance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appearance").value("CLASSIC"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void administratorCanUpdateButPresidentCannot() throws Exception {
        mvc.perform(put("/api/settings/appearance")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appearance\":\"EDITORIAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appearance").value("EDITORIAL"));

        mvc.perform(put("/api/settings/appearance")
                        .header("Authorization", "Bearer " + presidentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appearance\":\"SPATIAL\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownAppearanceIsRejectedAsBadRequest() throws Exception {
        mvc.perform(put("/api/settings/appearance")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appearance\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
    }

    private String tokenFor(String studentNo, String name, Role role) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES (?, ?, 'test-hash', ?, 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name, role.name());
        if (id == null) throw new IllegalStateException("测试账号创建失败");
        return tokens.issue(id, studentNo, name, role);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("ca-attendance-appearance-web-test-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
