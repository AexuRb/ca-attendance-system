package com.ca.attendance.attendance;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttendanceBulkReviewIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final int RECORD_COUNT = 601;

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
        jdbc.update("DELETE FROM operation_logs");
        jdbc.update("DELETE FROM public_attendance_submissions");
        jdbc.update("DELETE FROM attendance_records");
        jdbc.update("DELETE FROM users WHERE student_no LIKE 'bulk-review-%'");

        adminId = insertUser("bulk-review-admin", "批量审核管理员", "ADMIN");
        memberId = insertUser("bulk-review-member", "批量审核成员", "MEMBER");
        AuthContext.set(new AuthUser(
                adminId,
                "bulk-review-admin",
                "批量审核管理员",
                Role.ADMIN,
                Instant.now().plusSeconds(3600)
        ));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void approveAllDoesNotOmitRecordsBeyondVisibleQueue() {
        insertPendingRecords(RECORD_COUNT);
        jdbc.update("""
                UPDATE attendance_records
                SET check_out_status = 'PENDING'
                WHERE id = (
                  SELECT MIN(id)
                  FROM attendance_records
                  WHERE student_no_snapshot = 'bulk-review-member'
                )
                """);
        AttendanceService.PendingReviewQueue queue = attendance.pendingQueue();

        AttendanceService.BulkReviewResult result = attendance.bulkReview(
                new AttendanceService.BulkReviewRequest(
                        queue.items().stream().map(AttendanceRecord::id).toList(),
                        "ALL",
                        "ALL_PENDING"
                )
        );

        assertEquals(500, queue.items().size());
        assertEquals(RECORD_COUNT, queue.recordCount());
        assertEquals(RECORD_COUNT + 1, queue.itemCount());
        assertTrue(queue.truncated());
        assertEquals(RECORD_COUNT, result.matched());
        assertEquals(RECORD_COUNT + 1, result.reviewed());
        assertEquals(0, pendingItemCount());
        assertEquals(RECORD_COUNT, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM attendance_records
                WHERE effective_status = 'VALID'
                  AND duration_minutes = 120
                  AND valid_hours = 2
                """, Integer.class));
        assertEquals(1, actionCount("BULK_REVIEW_ATTENDANCE"));
        assertEquals(0, actionCount("REVIEW_ATTENDANCE"));
    }

    @Test
    void approveAllIncludesPendingRecordCommittedWhileOperationWaitsForWriteLock() throws Exception {
        String databaseUrl = "jdbc:sqlite:" + STORAGE_ROOT.resolve("data/attendance.db").toString().replace('\\', '/');
        AuthUser admin = new AuthUser(
                adminId,
                "bulk-review-admin",
                "批量审核管理员",
                Role.ADMIN,
                Instant.now().plusSeconds(3600)
        );

        try (Connection writer = DriverManager.getConnection(databaseUrl)) {
            writer.setAutoCommit(false);
            try (PreparedStatement statement = writer.prepareStatement("""
                    INSERT INTO attendance_records (
                      user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                      is_duty_day, within_duty_period, check_in_time, check_out_time,
                      check_in_status, check_out_status, effective_status, source
                    ) VALUES (
                      ?, 'bulk-review-member', '批量审核成员', '2026-08-12', 3,
                      1, 1, '2026-08-12 14:00:00', '2026-08-12 16:00:00',
                      'PENDING', 'APPROVED', 'PENDING', 'PUBLIC'
                    )
                    """)) {
                statement.setLong(1, memberId);
                statement.executeUpdate();
            }

            CompletableFuture<AttendanceService.BulkReviewResult> review = CompletableFuture.supplyAsync(() -> {
                AuthContext.set(admin);
                try {
                    return attendance.bulkReview(new AttendanceService.BulkReviewRequest(
                            List.of(),
                            "ALL",
                            "ALL_PENDING"
                    ));
                } finally {
                    AuthContext.clear();
                }
            });

            Thread.sleep(200);
            assertTrue(!review.isDone(), "批量审核应等待正在提交的待审核记录");
            writer.commit();

            AttendanceService.BulkReviewResult result = review.orTimeout(5, TimeUnit.SECONDS).join();
            assertEquals(1, result.matched());
            assertEquals(1, result.reviewed());
        }

        assertEquals(0, pendingItemCount());
    }

    private void insertPendingRecords(int count) {
        List<Object[]> rows = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rows.add(new Object[]{memberId, "2026-08-12 14:00:00", "2026-08-12 16:00:00"});
        }
        jdbc.batchUpdate("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                  is_duty_day, within_duty_period, check_in_time, check_out_time,
                  check_in_status, check_out_status, effective_status, source
                ) VALUES (
                  ?, 'bulk-review-member', '批量审核成员', '2026-08-12', 3,
                  1, 1, ?, ?, 'PENDING', 'APPROVED', 'PENDING', 'PUBLIC'
                )
                """, rows);
    }

    private int pendingItemCount() {
        Integer count = jdbc.queryForObject("""
                SELECT COALESCE(SUM(
                  CASE WHEN check_in_status = 'PENDING' THEN 1 ELSE 0 END
                  + CASE WHEN check_out_status = 'PENDING' THEN 1 ELSE 0 END
                ), 0)
                FROM attendance_records
                """, Integer.class);
        return count == null ? 0 : count;
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
            throw new IllegalStateException("测试成员创建失败");
        }
        return id;
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("ca-attendance-bulk-review-test-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
