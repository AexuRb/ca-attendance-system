package com.ca.attendance.attendance;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.Role;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.settings.AttendancePolicyService;
import com.ca.attendance.stats.StatsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttendanceFlowIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();

    @Autowired
    private AttendanceService attendance;

    @Autowired
    private AttendancePolicyService policies;

    @Autowired
    private StatsService stats;

    @Autowired
    private JdbcTemplate jdbc;

    private long adminId;
    private long memberId;
    private long ministerId;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM public_attendance_submissions");
        jdbc.update("DELETE FROM attendance_records");
        jdbc.update("DELETE FROM operation_logs");
        jdbc.update("DELETE FROM app_settings WHERE setting_key LIKE 'ATTENDANCE_REQUIRE_%'");
        jdbc.update("DELETE FROM users WHERE student_no LIKE 'attendance-flow-%'");
        jdbc.update("UPDATE duty_weekday_settings SET enabled = 1");
        jdbc.update("""
                INSERT INTO app_settings (setting_key, setting_value, description)
                VALUES ('DUTY_TIME_PERIODS', '[{"startTime":"00:00","endTime":"23:59","enabled":true}]', '测试时段')
                ON CONFLICT (setting_key) DO UPDATE SET setting_value = excluded.setting_value
                """);

        adminId = insertUser("attendance-flow-admin", "流程测试管理员", "ADMIN");
        memberId = insertUser("attendance-flow-member", "流程测试成员", "MEMBER");
        ministerId = insertUser("attendance-flow-minister", "流程测试部长", "MINISTER");
        authenticate(adminId, "attendance-flow-admin", "流程测试管理员", Role.ADMIN);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void memberFlowIsIdempotentReviewableRepeatableAndIncludedInStatistics() {
        AttendanceService.PublicLookupResponse checkInLookup = attendance.lookupByInput("attendance-flow-member");
        AttendanceService.SubmitResponse checkIn = attendance.submitPublicSelection(
                checkInLookup.memberToken(),
                "attendance-flow-check-in-001"
        );
        AttendanceService.SubmitResponse retry = attendance.submitPublicSelection(
                checkInLookup.memberToken(),
                "attendance-flow-check-in-001"
        );

        assertEquals("CHECK_IN", checkIn.action());
        assertEquals("PENDING", checkIn.status());
        assertEquals(checkIn.recordId(), retry.recordId());
        assertEquals(1, recordCount(memberId));

        jdbc.update(
                "UPDATE attendance_records SET check_in_time = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(91),
                checkIn.recordId()
        );
        AttendanceService.PublicLookupResponse checkOutLookup = attendance.lookupByInput("attendance-flow-member");
        assertEquals("CHECK_OUT", checkOutLookup.action());
        AttendanceService.SubmitResponse checkOut = attendance.submitPublicSelection(
                checkOutLookup.memberToken(),
                "attendance-flow-check-out-001"
        );

        AttendanceRecord pending = attendanceRecord(checkOut.recordId());
        assertEquals("PENDING", pending.checkInStatus());
        assertEquals("PENDING", pending.checkOutStatus());
        assertEquals("PENDING", pending.effectiveStatus());

        AttendanceService.BulkReviewResult reviewed = attendance.bulkReview(
                new AttendanceService.BulkReviewRequest(List.of(checkOut.recordId()), "ALL")
        );
        AttendanceRecord approved = attendanceRecord(checkOut.recordId());

        assertEquals(2, reviewed.reviewed());
        assertEquals(0, reviewed.skipped());
        assertTrue(reviewed.errors().isEmpty());
        assertEquals("VALID", approved.effectiveStatus());
        assertEquals(2, approved.validHours());
        assertEquals(2, stats.summary(LocalDate.now(), LocalDate.now()).getFirst().attendanceHours().intValue());

        AttendanceService.PublicLookupResponse nextLookup = attendance.lookupByInput("attendance-flow-member");
        assertEquals("CHECK_IN", nextLookup.action());
        attendance.submitPublicSelection(nextLookup.memberToken(), "attendance-flow-check-in-002");
        assertEquals(2, recordCount(memberId));
    }

    @Test
    void ministerCheckInAndCheckOutAreAutomaticallyApproved() {
        AttendanceService.PublicLookupResponse checkInLookup = attendance.lookupByInput("attendance-flow-minister");
        AttendanceService.SubmitResponse checkIn = attendance.submitPublicSelection(
                checkInLookup.memberToken(),
                "minister-flow-check-in"
        );
        jdbc.update(
                "UPDATE attendance_records SET check_in_time = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(60),
                checkIn.recordId()
        );
        AttendanceService.PublicLookupResponse checkOutLookup = attendance.lookupByInput("attendance-flow-minister");
        attendance.submitPublicSelection(checkOutLookup.memberToken(), "minister-flow-check-out");

        AttendanceRecord record = attendanceRecord(checkIn.recordId());
        assertEquals("AUTO_APPROVED", record.checkInStatus());
        assertEquals("AUTO_APPROVED", record.checkOutStatus());
        assertEquals("VALID", record.effectiveStatus());
        assertFalse(attendance.pending().stream().anyMatch(item -> item.userId() == ministerId));
    }

    @Test
    void strictRulesAreSnapshottedAndDoNotChangeWithLaterSettings() {
        policies.update(new AttendancePolicyService.UpdateAttendancePolicyRequest(true, true));
        jdbc.update("UPDATE duty_weekday_settings SET enabled = 0 WHERE weekday = ?",
                LocalDate.now().getDayOfWeek().getValue());
        jdbc.update("""
                UPDATE app_settings
                SET setting_value = '[{"startTime":"23:58","endTime":"23:59","enabled":true}]'
                WHERE setting_key = 'DUTY_TIME_PERIODS'
                """);

        AttendanceService.PublicLookupResponse lookup = attendance.lookupByInput("attendance-flow-member");
        assertTrue(lookup.message().contains("不计入有效时长"));
        AttendanceService.SubmitResponse submitted = attendance.submitPublicSelection(
                lookup.memberToken(),
                "strict-flow-check-in"
        );
        AttendanceRecord strictRecord = attendanceRecord(submitted.recordId());

        assertTrue(strictRecord.requireDutyDay());
        assertTrue(strictRecord.requireDutyPeriod());
        assertEquals("INVALID", strictRecord.effectiveStatus());

        policies.update(new AttendancePolicyService.UpdateAttendancePolicyRequest(false, false));
        attendance.recompute(strictRecord.id());

        AttendanceRecord recomputed = attendanceRecord(strictRecord.id());
        assertTrue(recomputed.requireDutyDay());
        assertTrue(recomputed.requireDutyPeriod());
        assertEquals("INVALID", recomputed.effectiveStatus());
    }

    @Test
    void rejectionRequiresAReasonAndInvalidatesTheRecord() {
        long recordId = insertPendingRecord(memberId, "attendance-flow-member", "流程测试成员");

        assertThrows(ApiException.class, () ->
                attendance.review(recordId, "CHECK_IN", "REJECT", "  "));
        assertEquals("PENDING", attendanceRecord(recordId).checkInStatus());

        attendance.review(recordId, "CHECK_IN", "REJECT", "  学号由他人误输  ");

        AttendanceRecord rejected = attendanceRecord(recordId);
        assertEquals("REJECTED", rejected.checkInStatus());
        assertEquals("INVALID", rejected.effectiveStatus());
        assertEquals(0, rejected.durationMinutes());
        assertEquals("学号由他人误输", rejected.checkInRejectReason());
    }

    @Test
    void manualCreateProducesAnAuditedValidRecord() {
        LocalDate date = LocalDate.now();

        AttendanceRecord created = attendance.manualCreate(new AttendanceService.ManualCreateRequest(
                "attendance-flow-member",
                date.atTime(14, 0),
                date.atTime(16, 0),
                "补录纸质值班记录"
        ));

        assertEquals("ADMIN_MANUAL", created.source());
        assertEquals("AUTO_APPROVED", created.checkInStatus());
        assertEquals("AUTO_APPROVED", created.checkOutStatus());
        assertEquals("VALID", created.effectiveStatus());
        assertEquals(2, created.validHours());
        assertEquals(1, actionCount("MANUAL_CREATE_ATTENDANCE"));
    }

    @Test
    void ministerPendingListExcludesTheirOwnRecord() {
        long ownRecordId = insertPendingRecord(
                ministerId,
                "attendance-flow-minister",
                "流程测试部长"
        );
        long memberRecordId = insertPendingRecord(
                memberId,
                "attendance-flow-member",
                "流程测试成员"
        );
        authenticate(
                ministerId,
                "attendance-flow-minister",
                "流程测试部长",
                Role.MINISTER
        );

        List<AttendanceRecord> pending = attendance.pending();

        assertFalse(pending.stream().anyMatch(record -> record.id() == ownRecordId));
        assertTrue(pending.stream().anyMatch(record -> record.id() == memberRecordId));
        assertThrows(ApiException.class, () ->
                attendance.review(ownRecordId, "CHECK_IN", "APPROVE", ""));
    }

    private AttendanceRecord attendanceRecord(long id) {
        return attendance.search(LocalDate.now(), LocalDate.now(), "", "").stream()
                .filter(record -> record.id() == id)
                .findFirst()
                .orElseThrow();
    }

    private int recordCount(long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM attendance_records WHERE user_id = ?",
                Integer.class,
                userId
        );
        return count == null ? 0 : count;
    }

    private long insertPendingRecord(long userId, String studentNo, String name) {
        LocalDate date = LocalDate.now();
        Long id = jdbc.queryForObject("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                  is_duty_day, within_duty_period, require_duty_day, require_duty_period,
                  check_in_time, check_out_time, check_in_status, check_out_status,
                  duration_minutes, valid_hours, effective_status, source
                )
                VALUES (?, ?, ?, ?, ?, 1, 1, 0, 0, ?, ?,
                        'PENDING', 'PENDING', 0, 0, 'PENDING', 'PUBLIC')
                RETURNING id
                """, Long.class, userId, studentNo, name, date, date.getDayOfWeek().getValue(),
                Timestamp.valueOf(date.atTime(14, 0)),
                Timestamp.valueOf(date.atTime(16, 0)));
        if (id == null) {
            throw new IllegalStateException("待审核测试记录创建失败");
        }
        return id;
    }

    private int actionCount(String actionType) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = ?",
                Integer.class,
                actionType
        );
        return count == null ? 0 : count;
    }

    private long insertUser(String studentNo, String name, String role) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES (?, ?, 'test-hash', ?, 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name, role);
        if (id == null) {
            throw new IllegalStateException("流程测试账号创建失败");
        }
        return id;
    }

    private void authenticate(long id, String studentNo, String name, Role role) {
        AuthContext.set(new AuthUser(id, studentNo, name, role, Instant.now().plusSeconds(3600)));
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("ca-attendance-flow-test-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
