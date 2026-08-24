package com.ca.attendance.common;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.auth.TokenService;
import com.ca.attendance.log.OperationLogService;
import org.junit.jupiter.api.AfterEach;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.remote.port=0")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JsonAndErrorContractIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final String ACTION = "JSON_CONTRACT_TEST";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OperationLogService logs;

    @Autowired
    private TokenService tokens;

    private MockMvc mvc;
    private long operatorId;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        operatorId = jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('json-contract-admin', 'JSON契约测试', 'test-hash', 'ADMIN', 'ACTIVE', 0)
                RETURNING id
                """, Long.class);
        AuthContext.set(new AuthUser(
                operatorId,
                "json-contract-admin",
                "JSON契约测试",
                Role.ADMIN,
                Instant.now().plusSeconds(300)
        ));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        jdbc.update("DELETE FROM operation_logs WHERE action_type = ?", ACTION);
        jdbc.update("DELETE FROM attendance_records WHERE user_id = ?", operatorId);
        jdbc.update("DELETE FROM users WHERE id = ?", operatorId);
    }

    @Test
    void writesApiAndAuditDatesAsIsoStrings() throws Exception {
        mvc.perform(get("/api/public/schedules/today").param("date", "2026-08-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-08-21"));

        LocalDate dutyDate = LocalDate.of(2026, 8, 21);
        jdbc.update("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                  check_in_time, check_out_time, check_in_status, check_out_status,
                  duration_minutes, valid_hours, effective_status
                )
                VALUES (?, 'json-contract-admin', 'JSON契约测试', ?, 5, ?, ?,
                        'APPROVED', 'APPROVED', 90, 1.5, 'VALID')
                """,
                operatorId,
                dutyDate,
                Timestamp.valueOf(dutyDate.atTime(14, 30)),
                Timestamp.valueOf(dutyDate.atTime(16, 0))
        );
        String token = tokens.issue(operatorId, "json-contract-admin", "JSON契约测试", Role.ADMIN);
        mvc.perform(get("/api/attendance/page")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-08-21")
                        .param("to", "2026-08-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].dutyDate").value("2026-08-21"))
                .andExpect(jsonPath("$.items[0].checkInTime").value("2026-08-21T14:30:00"))
                .andExpect(jsonPath("$.items[0].checkOutTime").value("2026-08-21T16:00:00"));

        AuthContext.set(new AuthUser(
                operatorId,
                "json-contract-admin",
                "JSON契约测试",
                Role.ADMIN,
                Instant.now().plusSeconds(300)
        ));
        logs.log(
                ACTION,
                "system",
                null,
                null,
                Map.of(
                        "trainingDate", LocalDate.of(2026, 8, 21),
                        "startTime", LocalTime.of(14, 30),
                        "createdAt", LocalDateTime.of(2026, 8, 21, 14, 30),
                        "exportedAt", Instant.parse("2026-08-21T06:30:00Z")
                ),
                "验证日期 JSON 契约"
        );

        String snapshot = jdbc.queryForObject(
                "SELECT after_data FROM operation_logs WHERE action_type = ? ORDER BY id DESC LIMIT 1",
                String.class,
                ACTION
        );
        org.assertj.core.api.Assertions.assertThat(snapshot)
                .contains("\"trainingDate\":\"2026-08-21\"")
                .contains("\"startTime\":\"14:30:00\"")
                .contains("\"createdAt\":\"2026-08-21T14:30:00\"")
                .contains("\"exportedAt\":\"2026-08-21T06:30:00Z\"")
                .doesNotContain("[2026,8,21]");
    }

    @Test
    void malformedJsonReturnsStableBadRequestBody() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("请求内容格式不正确"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void invalidDateReturnsBadRequestInsteadOfServerError() throws Exception {
        mvc.perform(get("/api/public/schedules/today").param("date", "2026-99-99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("请求参数格式不正确"));
    }

    @Test
    void missingRequiredParameterAndHeaderReturnBadRequest() throws Exception {
        mvc.perform(get("/api/public/attendance/lookup"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("请求参数不完整或格式错误"));

        mvc.perform(post("/api/desktop/shutdown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("请求参数不完整或格式错误"));
    }

    @Test
    void unsupportedContentTypeReturnsStableClientError() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("studentNo=json-contract-admin"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message").value("请求内容类型不支持"));
    }

    @Test
    void unsupportedMethodKeepsAllowHeaderAndErrorContract() throws Exception {
        mvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(content().string(not(containsString("服务器内部错误"))))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").value("请求方法不支持"));
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("ca-json-error-contract-test-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
