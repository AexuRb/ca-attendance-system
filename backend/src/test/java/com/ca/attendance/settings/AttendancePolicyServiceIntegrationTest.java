package com.ca.attendance.settings;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttendancePolicyServiceIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();

    @Autowired
    private AttendancePolicyService policies;

    @Autowired
    private JdbcTemplate jdbc;

    private long adminId;
    private long presidentId;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_attendance_policy_log");
        jdbc.update("DELETE FROM operation_logs");
        jdbc.update("DELETE FROM app_settings WHERE setting_key LIKE 'ATTENDANCE_REQUIRE_%'");
        jdbc.update("DELETE FROM users WHERE student_no LIKE 'policy-test-%'");
        adminId = insertUser("policy-test-admin", "规则测试管理员", "ADMIN");
        presidentId = insertUser("policy-test-president", "规则测试会长", "PRESIDENT");
        authenticate(adminId, "policy-test-admin", "规则测试管理员", Role.ADMIN);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        jdbc.execute("DROP TRIGGER IF EXISTS fail_attendance_policy_log");
    }

    @Test
    void defaultsPreserveReviewBasedEligibility() {
        AttendancePolicyService.AttendancePolicy policy = policies.current();

        assertFalse(policy.requireDutyDay());
        assertFalse(policy.requireDutyPeriod());
    }

    @Test
    void administratorCanUpdateBothEligibilityRules() {
        AttendancePolicyService.AttendancePolicy saved = policies.update(
                new AttendancePolicyService.UpdateAttendancePolicyRequest(true, true)
        );

        assertTrue(saved.requireDutyDay());
        assertTrue(saved.requireDutyPeriod());
        assertEquals(saved, policies.current());
        assertEquals(1, actionCount("UPDATE_ATTENDANCE_POLICY"));
    }

    @Test
    void presidentCanReadButCannotUpdateEligibilityRules() {
        authenticate(presidentId, "policy-test-president", "规则测试会长", Role.PRESIDENT);

        assertEquals(new AttendancePolicyService.AttendancePolicy(false, false), policies.readForManager());
        assertThrows(ApiException.class, () -> policies.update(
                new AttendancePolicyService.UpdateAttendancePolicyRequest(true, false)
        ));
    }

    @Test
    void updateRollsBackWhenAuditLogFails() {
        jdbc.execute("""
                CREATE TRIGGER fail_attendance_policy_log
                BEFORE INSERT ON operation_logs
                WHEN NEW.action_type = 'UPDATE_ATTENDANCE_POLICY'
                BEGIN
                  SELECT RAISE(ABORT, 'forced policy log failure');
                END
                """);

        assertThrows(DataAccessException.class, () -> policies.update(
                new AttendancePolicyService.UpdateAttendancePolicyRequest(true, true)
        ));

        assertEquals(new AttendancePolicyService.AttendancePolicy(false, false), policies.current());
    }

    private long insertUser(String studentNo, String name, String role) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES (?, ?, 'test-hash', ?, 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name, role);
        if (id == null) {
            throw new IllegalStateException("测试账号创建失败");
        }
        return id;
    }

    private void authenticate(long id, String studentNo, String name, Role role) {
        AuthContext.set(new AuthUser(id, studentNo, name, role, Instant.now().plusSeconds(3600)));
    }

    private int actionCount(String actionType) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = ?",
                Integer.class,
                actionType
        );
        return count == null ? 0 : count;
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("ca-attendance-policy-test-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
