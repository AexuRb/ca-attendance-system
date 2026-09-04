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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(AttendanceFlowIntegrationTest.FixedClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttendanceFlowIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final java.time.ZoneId TEST_ZONE = java.time.ZoneId.systemDefault();
    private static final Clock FLOW_CLOCK = Clock.fixed(
            LocalDate.now().atTime(23, 0).atZone(TEST_ZONE).toInstant(),
            TEST_ZONE
    );

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock testClock() {
            return FLOW_CLOCK;
        }
    }

    @Autowired
    private AttendanceService attendance;

    @Autowired
    private AttendanceRepository records;

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
                Timestamp.valueOf(LocalDateTime.now(FLOW_CLOCK).minusMinutes(91)),
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
                Timestamp.valueOf(LocalDateTime.now(FLOW_CLOCK).minusMinutes(60)),
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
    void publicSubmissionPrunesReceiptsOlderThanThirtyDays() {
        LocalDateTime now = LocalDateTime.now(FLOW_CLOCK);
        jdbc.update("""
                INSERT INTO public_attendance_submissions (
                  request_id, student_no, record_id, action, name, submitted_at,
                  review_status, message, created_at
                ) VALUES
                  ('attendance-old-receipt', 'attendance-flow-member', 800001,
                   'CHECK_IN', '流程测试成员', ?,
                   'PENDING', '过期回执', ?),
                  ('attendance-boundary-receipt', 'attendance-flow-member', 800002,
                   'CHECK_IN', '流程测试成员', ?,
                   'PENDING', '边界回执', ?),
                  ('attendance-recent-receipt', 'attendance-flow-member', 800002,
                   'CHECK_IN', '流程测试成员', ?,
                   'PENDING', '保留回执', ?)
                """,
                Timestamp.valueOf(now.minusDays(31)),
                Timestamp.valueOf(now.minusDays(31)),
                Timestamp.valueOf(now.minusDays(30)),
                Timestamp.valueOf(now.minusDays(30)),
                Timestamp.valueOf(now.minusDays(29)),
                Timestamp.valueOf(now.minusDays(29)));

        AttendanceService.PublicLookupResponse lookup = attendance.lookupByInput("attendance-flow-member");
        attendance.submitPublicSelection(lookup.memberToken(), "attendance-retention-check");

        assertEquals(0, submissionCount("attendance-old-receipt"));
        assertEquals(1, submissionCount("attendance-boundary-receipt"));
        assertEquals(1, submissionCount("attendance-recent-receipt"));
        assertEquals(1, submissionCount("attendance-retention-check"));
    }

    @Test
    void concurrentRetriesProduceOneAttendanceRecordAndOneReceipt() throws Exception {
        AttendanceService.PublicLookupResponse lookup = attendance.lookupByInput("attendance-flow-member");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<AttendanceService.SubmitResponse> first = executor.submit(() -> {
                ready.countDown();
                start.await(2, TimeUnit.SECONDS);
                return attendance.submitPublicSelection(
                        lookup.memberToken(),
                        "attendance-concurrent-retry"
                );
            });
            Future<AttendanceService.SubmitResponse> second = executor.submit(() -> {
                ready.countDown();
                start.await(2, TimeUnit.SECONDS);
                return attendance.submitPublicSelection(
                        lookup.memberToken(),
                        "attendance-concurrent-retry"
                );
            });
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();

            AttendanceService.SubmitResponse firstResponse = first.get(5, TimeUnit.SECONDS);
            AttendanceService.SubmitResponse secondResponse = second.get(5, TimeUnit.SECONDS);
            assertEquals(firstResponse.recordId(), secondResponse.recordId());
            assertEquals(firstResponse.action(), secondResponse.action());
        }

        assertEquals(1, recordCount(memberId));
        assertEquals(1, submissionCount("attendance-concurrent-retry"));
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
    void manualCreateAllowsNonDutyDatesAndPreservesEligibilitySnapshotOnUpdate() {
        LocalDate date = LocalDate.now().minusDays(1);
        LocalDateTime checkIn = date.atTime(14, 0);
        LocalDateTime checkOut = date.atTime(16, 0);
        policies.update(new AttendancePolicyService.UpdateAttendancePolicyRequest(true, true));
        jdbc.update("UPDATE duty_weekday_settings SET enabled = 0 WHERE weekday = ?",
                date.getDayOfWeek().getValue());
        jdbc.update("""
                UPDATE app_settings
                SET setting_value = '[{"startTime":"23:58","endTime":"23:59","enabled":true}]'
                WHERE setting_key = 'DUTY_TIME_PERIODS'
                """);

        AttendanceRecord created = attendance.manualCreate(new AttendanceService.ManualCreateRequest(
                "attendance-flow-member", checkIn, checkOut, "补录非值班时段记录"
        ));

        assertFalse(created.dutyDay());
        assertFalse(created.withinDutyPeriod());
        assertTrue(created.requireDutyDay());
        assertTrue(created.requireDutyPeriod());
        assertEquals("INVALID", created.effectiveStatus());

        policies.update(new AttendancePolicyService.UpdateAttendancePolicyRequest(false, false));
        jdbc.update("UPDATE duty_weekday_settings SET enabled = 1 WHERE weekday = ?",
                date.getDayOfWeek().getValue());
        jdbc.update("""
                UPDATE app_settings
                SET setting_value = '[{"startTime":"00:00","endTime":"23:59","enabled":true}]'
                WHERE setting_key = 'DUTY_TIME_PERIODS'
                """);

        AttendanceRecord updated = attendance.manualUpdate(created.id(), new AttendanceService.ManualUpdateRequest(
                checkIn.plusMinutes(5), checkOut, "APPROVED", "APPROVED", "修正签到时间"
        ));

        assertFalse(updated.dutyDay());
        assertFalse(updated.withinDutyPeriod());
        assertTrue(updated.requireDutyDay());
        assertTrue(updated.requireDutyPeriod());
        assertEquals("INVALID", updated.effectiveStatus());

        AttendanceRecord reevaluated = attendance.manualUpdate(created.id(), new AttendanceService.ManualUpdateRequest(
                checkIn.plusMinutes(10), checkOut,
                "APPROVED", "APPROVED", "按当前规则重新评估", true
        ));

        assertTrue(reevaluated.dutyDay());
        assertTrue(reevaluated.withinDutyPeriod());
        assertFalse(reevaluated.requireDutyDay());
        assertFalse(reevaluated.requireDutyPeriod());
        assertEquals("VALID", reevaluated.effectiveStatus());
    }

    @Test
    void manualRecordsAllowAdjacentPeriodsButRejectOverlaps() {
        LocalDate date = LocalDate.now().minusDays(1);
        AttendanceRecord first = attendance.manualCreate(new AttendanceService.ManualCreateRequest(
                "attendance-flow-member", date.atTime(14, 0), date.atTime(16, 0), "第一段值班"
        ));

        ApiException overlap = assertThrows(ApiException.class, () ->
                attendance.manualCreate(new AttendanceService.ManualCreateRequest(
                        "attendance-flow-member", date.atTime(15, 0), date.atTime(17, 0), "重叠值班"
                )));
        AttendanceRecord adjacent = attendance.manualCreate(new AttendanceService.ManualCreateRequest(
                "attendance-flow-member", date.atTime(16, 0), date.atTime(18, 0), "相邻值班"
        ));

        assertTrue(overlap.getMessage().contains("重叠"));
        assertEquals(2, recordCount(memberId));
        assertEquals(date.atTime(14, 0), attendanceRecord(first.id(), date).checkInTime());
        assertEquals(date.atTime(16, 0), attendanceRecord(adjacent.id(), date).checkInTime());

        ApiException updateOverlap = assertThrows(ApiException.class, () ->
                attendance.manualUpdate(adjacent.id(), new AttendanceService.ManualUpdateRequest(
                        date.atTime(15, 30), date.atTime(17, 30),
                        "APPROVED", "APPROVED", "尝试改为重叠区间"
                )));

        assertTrue(updateOverlap.getMessage().contains("重叠"));
        assertEquals(date.atTime(16, 0), attendanceRecord(adjacent.id(), date).checkInTime());
    }

    @Test
    void attendancePaginationUsesIdAsTieBreakerForEqualTimes() {
        LocalDate date = LocalDate.now().minusDays(1);
        LocalDateTime checkIn = date.atTime(14, 0);
        List<Long> ids = List.of(
                insertApprovedRecord(memberId, "attendance-flow-member", "流程测试成员", checkIn),
                insertApprovedRecord(memberId, "attendance-flow-member", "流程测试成员", checkIn),
                insertApprovedRecord(memberId, "attendance-flow-member", "流程测试成员", checkIn),
                insertApprovedRecord(memberId, "attendance-flow-member", "流程测试成员", checkIn)
        );

        AttendanceRepository.AttendancePage firstPage = attendance.searchPage(date, date, "", "", 1, 2);
        AttendanceRepository.AttendancePage secondPage = attendance.searchPage(date, date, "", "", 2, 2);

        assertEquals(List.of(ids.get(3), ids.get(2)),
                firstPage.items().stream().map(AttendanceRecord::id).toList());
        assertEquals(List.of(ids.get(1), ids.get(0)),
                secondPage.items().stream().map(AttendanceRecord::id).toList());
    }

    @Test
    void batchEffectiveUpdateStoresTheOperator() {
        LocalDate date = LocalDate.now().minusDays(1);
        long recordId = insertApprovedRecord(
                memberId, "attendance-flow-member", "流程测试成员", date.atTime(14, 0));
        jdbc.update("UPDATE attendance_records SET updated_by = NULL WHERE id = ?", recordId);

        int[] counts = records.batchUpdateEffective(List.of(
                new AttendanceRepository.EffectiveUpdate(recordId, 120, 2, "VALID")
        ), adminId);

        assertEquals(1, counts.length);
        assertEquals(1, counts[0]);
        assertEquals(adminId, jdbc.queryForObject(
                "SELECT updated_by FROM attendance_records WHERE id = ?", Long.class, recordId));
    }

    @Test
    void concurrentOverlappingManualCreatesAllowOnlyOneRecord() throws Exception {
        LocalDate date = LocalDate.now().minusDays(1);
        LocalDateTime checkIn = date.atTime(10, 0);
        LocalDateTime checkOut = date.atTime(12, 0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> manualCreateOutcome(ready, start, checkIn, checkOut));
            Future<String> second = executor.submit(() -> manualCreateOutcome(ready, start, checkIn, checkOut));
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();

            List<String> outcomes = List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            );
            assertEquals(1, outcomes.stream().filter("CREATED"::equals).count());
            assertEquals(1, outcomes.stream().filter("CONFLICT"::equals).count());
        }

        assertEquals(1, recordCount(memberId));
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
        return attendanceRecord(id, LocalDate.now());
    }

    private AttendanceRecord attendanceRecord(long id, LocalDate date) {
        return attendance.search(date, date, "", "").stream()
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

    private String manualCreateOutcome(CountDownLatch ready, CountDownLatch start,
                                       LocalDateTime checkIn, LocalDateTime checkOut) throws InterruptedException {
        authenticate(adminId, "attendance-flow-admin", "流程测试管理员", Role.ADMIN);
        ready.countDown();
        start.await(2, TimeUnit.SECONDS);
        try {
            attendance.manualCreate(new AttendanceService.ManualCreateRequest(
                    "attendance-flow-member", checkIn, checkOut, "并发补录"
            ));
            return "CREATED";
        } catch (ApiException ex) {
            return ex.getMessage().contains("重叠") ? "CONFLICT" : "UNEXPECTED";
        } finally {
            AuthContext.clear();
        }
    }

    private int submissionCount(String requestId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public_attendance_submissions WHERE request_id = ?",
                Integer.class,
                requestId
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

    private long insertApprovedRecord(long userId, String studentNo, String name, LocalDateTime checkIn) {
        Long id = jdbc.queryForObject("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                  is_duty_day, within_duty_period, require_duty_day, require_duty_period,
                  check_in_time, check_out_time, check_in_status, check_out_status,
                  duration_minutes, valid_hours, effective_status, source
                )
                VALUES (?, ?, ?, ?, ?, 1, 1, 0, 0, ?, ?,
                        'APPROVED', 'APPROVED', 120, 2, 'VALID', 'ADMIN_MANUAL')
                RETURNING id
                """, Long.class, userId, studentNo, name, checkIn.toLocalDate(),
                checkIn.toLocalDate().getDayOfWeek().getValue(),
                Timestamp.valueOf(checkIn), Timestamp.valueOf(checkIn.plusHours(2)));
        if (id == null) {
            throw new IllegalStateException("考勤测试记录创建失败");
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
