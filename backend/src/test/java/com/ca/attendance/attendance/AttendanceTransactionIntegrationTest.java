package com.ca.attendance.attendance;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttendanceTransactionIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final LocalDate DUTY_DATE = LocalDate.of(2026, 7, 27);

    @Autowired
    private AttendanceService attendance;

    @Autowired
    private JdbcTemplate jdbc;

    private long adminId;
    private long memberId;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    @BeforeEach
    void setUp() {
        dropFailureTriggers();
        jdbc.update("DELETE FROM public_attendance_submissions");
        jdbc.update("DELETE FROM attendance_records");
        jdbc.update("DELETE FROM operation_logs");
        jdbc.update("DELETE FROM users WHERE student_no LIKE 'attendance-tx-%'");

        adminId = insertUser("attendance-tx-admin", "事务测试管理员", "ADMIN");
        memberId = insertUser("attendance-tx-member", "事务测试成员", "MEMBER");
        AuthContext.set(new AuthUser(
                adminId,
                "attendance-tx-admin",
                "事务测试管理员",
                Role.ADMIN,
                Instant.now().plusSeconds(3600)
        ));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        dropFailureTriggers();
    }

    @Test
    void manualUpdateRollsBackRecordWhenAuditLogFails() {
        long recordId = insertRecord("APPROVED", "APPROVED", "VALID");
        failAuditFor("MANUAL_UPDATE_ATTENDANCE");

        assertThrows(DataAccessException.class, () -> attendance.manualUpdate(
                recordId,
                new AttendanceService.ManualUpdateRequest(
                        DUTY_DATE.atTime(14, 30),
                        DUTY_DATE.atTime(16, 30),
                        "APPROVED",
                        "APPROVED",
                        "事务回滚测试"
                )
        ));

        assertEquals("2026-07-27 14:00:00", text(recordId, "check_in_time"));
        assertEquals("2026-07-27 16:00:00", text(recordId, "check_out_time"));
        assertEquals(120, number(recordId, "duration_minutes"));
        assertEquals("VALID", text(recordId, "effective_status"));
        assertEquals(0, actionCount("MANUAL_UPDATE_ATTENDANCE"));
    }

    @Test
    void manualCreateRollsBackInsertedRecordWhenAuditLogFails() {
        failAuditFor("MANUAL_CREATE_ATTENDANCE");

        assertThrows(DataAccessException.class, () -> attendance.manualCreate(
                new AttendanceService.ManualCreateRequest(
                        "attendance-tx-member",
                        DUTY_DATE.atTime(14, 0),
                        DUTY_DATE.atTime(16, 0),
                        "事务回滚测试"
                )
        ));

        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM attendance_records
                WHERE user_id = ? AND source = 'ADMIN_MANUAL'
                """, Integer.class, memberId));
        assertEquals(0, actionCount("MANUAL_CREATE_ATTENDANCE"));
    }

    @Test
    void reviewRollsBackStatusAndEffectiveDurationWhenAuditLogFails() {
        long recordId = insertRecord("PENDING", "APPROVED", "PENDING");
        failAuditFor("REVIEW_ATTENDANCE");

        assertThrows(DataAccessException.class, () ->
                attendance.review(recordId, "CHECK_IN", "APPROVE", "事务回滚测试"));

        assertEquals("PENDING", text(recordId, "check_in_status"));
        assertEquals("PENDING", text(recordId, "effective_status"));
        assertEquals(0, number(recordId, "duration_minutes"));
        assertNull(value(recordId, "check_in_reviewed_by"));
        assertEquals(0, actionCount("REVIEW_ATTENDANCE"));
    }

    @Test
    void approvalReasonIsNotStoredAsARejectReason() {
        long recordId = insertRecord("PENDING", "APPROVED", "PENDING");

        attendance.review(recordId, "CHECK_IN", "APPROVE", "管理员确认通过");

        assertEquals("APPROVED", text(recordId, "check_in_status"));
        assertNull(value(recordId, "check_in_reject_reason"));
    }

    @Test
    void manualUpdateClearsStaleReviewMetadata() {
        long recordId = insertRecord("APPROVED", "APPROVED", "VALID");
        jdbc.update("""
                UPDATE attendance_records
                SET check_in_reviewed_by = ?, check_out_reviewed_by = ?,
                    check_in_reviewed_at = datetime('now', 'localtime'),
                    check_out_reviewed_at = datetime('now', 'localtime'),
                    check_in_reject_reason = '旧签到原因',
                    check_out_reject_reason = '旧签退原因'
                WHERE id = ?
                """, adminId, adminId, recordId);

        attendance.manualUpdate(recordId, new AttendanceService.ManualUpdateRequest(
                DUTY_DATE.atTime(14, 5),
                DUTY_DATE.atTime(16, 5),
                "APPROVED",
                "APPROVED",
                "重新核对记录"
        ));

        assertNull(value(recordId, "check_in_reviewed_by"));
        assertNull(value(recordId, "check_out_reviewed_by"));
        assertNull(value(recordId, "check_in_reviewed_at"));
        assertNull(value(recordId, "check_out_reviewed_at"));
        assertNull(value(recordId, "check_in_reject_reason"));
        assertNull(value(recordId, "check_out_reject_reason"));
    }

    @Test
    void bulkReviewRollsBackEarlierRecordsWhenLaterUpdateFails() {
        long firstId = insertRecord("PENDING", "APPROVED", "PENDING");
        long secondId = insertRecord("PENDING", "APPROVED", "PENDING");
        jdbc.execute("""
                CREATE TRIGGER fail_second_bulk_review_update
                BEFORE UPDATE OF check_in_status ON attendance_records
                WHEN OLD.id = %d AND NEW.check_in_status = 'APPROVED'
                BEGIN
                  SELECT RAISE(ABORT, 'forced bulk review update failure');
                END
                """.formatted(secondId));

        assertThrows(DataAccessException.class, () -> attendance.bulkReview(
                new AttendanceService.BulkReviewRequest(List.of(firstId, secondId), "CHECK_IN")
        ));

        assertEquals("PENDING", text(firstId, "check_in_status"));
        assertEquals("PENDING", text(secondId, "check_in_status"));
        assertEquals("PENDING", text(firstId, "effective_status"));
        assertEquals("PENDING", text(secondId, "effective_status"));
        assertEquals(0, actionCount("REVIEW_ATTENDANCE"));
        assertEquals(0, actionCount("BULK_REVIEW_ATTENDANCE"));
    }

    @Test
    void bulkReviewRollsBackRecordsWhenSummaryAuditLogFails() {
        long firstId = insertRecord("PENDING", "APPROVED", "PENDING");
        long secondId = insertRecord("PENDING", "APPROVED", "PENDING");
        failAuditFor("BULK_REVIEW_ATTENDANCE");

        assertThrows(DataAccessException.class, () -> attendance.bulkReview(
                new AttendanceService.BulkReviewRequest(List.of(firstId, secondId), "CHECK_IN")
        ));

        assertEquals("PENDING", text(firstId, "check_in_status"));
        assertEquals("PENDING", text(secondId, "check_in_status"));
        assertEquals("PENDING", text(firstId, "effective_status"));
        assertEquals("PENDING", text(secondId, "effective_status"));
        assertEquals(0, actionCount("BULK_REVIEW_ATTENDANCE"));
    }

    private long insertUser(String studentNo, String name, String role) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES (?, ?, 'test-hash', ?, 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name, role);
        if (id == null) {
            throw new IllegalStateException("测试成员创建失败");
        }
        return id;
    }

    private long insertRecord(String checkInStatus, String checkOutStatus, String effectiveStatus) {
        int durationMinutes = "VALID".equals(effectiveStatus) ? 120 : 0;
        int validHours = "VALID".equals(effectiveStatus) ? 2 : 0;
        Long id = jdbc.queryForObject("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                  is_duty_day, within_duty_period, check_in_time, check_out_time,
                  check_in_status, check_out_status, duration_minutes, valid_hours,
                  effective_status, source
                )
                VALUES (?, 'attendance-tx-member', '事务测试成员', ?, 1, 1, 1, ?, ?,
                        ?, ?, ?, ?, ?, 'PUBLIC')
                RETURNING id
                """, Long.class,
                memberId,
                java.sql.Date.valueOf(DUTY_DATE),
                Timestamp.valueOf(DUTY_DATE.atTime(14, 0)),
                Timestamp.valueOf(DUTY_DATE.atTime(16, 0)),
                checkInStatus,
                checkOutStatus,
                durationMinutes,
                validHours,
                effectiveStatus);
        if (id == null) {
            throw new IllegalStateException("测试签到记录创建失败");
        }
        return id;
    }

    private void failAuditFor(String actionType) {
        jdbc.execute("""
                CREATE TRIGGER fail_attendance_audit_log
                BEFORE INSERT ON operation_logs
                WHEN NEW.action_type = '%s'
                BEGIN
                  SELECT RAISE(ABORT, 'forced attendance audit failure');
                END
                """.formatted(actionType));
    }

    private void dropFailureTriggers() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_attendance_audit_log");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_second_bulk_review_log");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_second_bulk_review_update");
    }

    private Object value(long recordId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM attendance_records WHERE id = ?",
                Object.class,
                recordId
        );
    }

    private String text(long recordId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM attendance_records WHERE id = ?",
                String.class,
                recordId
        );
    }

    private int number(long recordId, String column) {
        Integer result = jdbc.queryForObject(
                "SELECT " + column + " FROM attendance_records WHERE id = ?",
                Integer.class,
                recordId
        );
        return result == null ? 0 : result;
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
            return Files.createTempDirectory("ca-attendance-transaction-test-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
