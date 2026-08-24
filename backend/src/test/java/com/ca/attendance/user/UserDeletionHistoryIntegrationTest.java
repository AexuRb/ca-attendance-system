package com.ca.attendance.user;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserDeletionHistoryIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    private long adminId;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    @BeforeEach
    void setUp() {
        cleanTestData();
        adminId = ensureAdmin();
        AuthContext.set(new AuthUser(
                adminId,
                "delete-admin",
                "删除保护测试管理员",
                Role.ADMIN,
                Instant.now().plusSeconds(3600)
        ));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void trainingParticipationPreventsPhysicalDeletion() {
        long targetId = insertMember("delete-training", "培训历史成员");
        long sessionId = insertTrainingSession("成员删除保护-培训参与", adminId);
        jdbc.update("""
                INSERT INTO training_participants (
                  session_id, user_id, student_no_snapshot, name_snapshot, duration_hours
                ) VALUES (?, ?, 'delete-training', '培训历史成员', 2)
                """, sessionId, targetId);

        assertDeletionBlocked(targetId, "培训记录");
        assertEquals(2, jdbc.queryForObject(
                "SELECT duration_hours FROM training_participants WHERE user_id = ?",
                Integer.class,
                targetId
        ));
    }

    @Test
    void attendanceRecordStillPreventsPhysicalDeletion() {
        long targetId = insertMember("delete-attendance", "签到历史成员");
        jdbc.update("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                  is_duty_day, within_duty_period, require_duty_day, require_duty_period,
                  check_in_time, check_out_time, check_in_status, check_out_status,
                  duration_minutes, valid_hours, effective_status, source
                ) VALUES (
                  ?, 'delete-attendance', '签到历史成员', date('now'), 1,
                  1, 1, 0, 0, datetime('now', '-2 hours'), datetime('now'),
                  'APPROVED', 'APPROVED', 120, 2, 'VALID', 'PUBLIC'
                )
                """, targetId);

        assertDeletionBlocked(targetId, "签到与审核记录");
    }

    @Test
    void fixedScheduleAssignmentPreventsPhysicalDeletion() {
        long targetId = insertMember("delete-schedule", "排班历史成员");
        Long slotId = jdbc.queryForObject("""
                INSERT INTO duty_schedule_slots (
                  weekday, start_time, end_time, note, created_by, updated_by
                ) VALUES (1, '14:00:00', '16:00:00', '成员删除保护-固定周表', ?, ?)
                RETURNING id
                """, Long.class, adminId, adminId);
        jdbc.update("""
                INSERT INTO duty_schedule_assignees (
                  slot_id, user_id, student_no_snapshot, name_snapshot
                ) VALUES (?, ?, 'delete-schedule', '排班历史成员')
                """, slotId, targetId);

        assertDeletionBlocked(targetId, "固定周表");
        assertEquals(targetId, jdbc.queryForObject(
                "SELECT user_id FROM duty_schedule_assignees WHERE slot_id = ?",
                Long.class,
                slotId
        ));
    }

    @Test
    void repairHandlingPreventsPhysicalDeletion() {
        long targetId = insertMember("delete-repair", "维修历史成员");
        jdbc.update("""
                INSERT INTO repair_cases (
                  case_no, agreement_type, owner_name, device_type, fault_description,
                  status, handler_user_id, handler_name_snapshot, remark, created_by, updated_by
                ) VALUES (
                  'DELETE-HISTORY-REPAIR', 'PERSONAL_DEVICE', '测试委托人', '笔记本电脑',
                  '无法开机', 'COMPLETED', ?, '维修历史成员', '成员删除保护-维修', ?, ?
                )
                """, targetId, adminId, adminId);

        assertDeletionBlocked(targetId, "维修事务");
        assertEquals(targetId, jdbc.queryForObject(
                "SELECT handler_user_id FROM repair_cases WHERE case_no = 'DELETE-HISTORY-REPAIR'",
                Long.class
        ));
    }

    @Test
    void operationLogAuthorshipPreventsPhysicalDeletion() {
        long targetId = insertMember("delete-log", "审计历史成员");
        jdbc.update("""
                INSERT INTO operation_logs (
                  operator_user_id, operator_student_no, operator_name, action_type,
                  target_type, target_id, reason
                ) VALUES (?, 'delete-log', '审计历史成员', 'TEST_ACTION', 'tests', 1, '成员删除保护测试')
                """, targetId);

        assertDeletionBlocked(targetId, "操作日志");
        assertEquals(targetId, jdbc.queryForObject(
                "SELECT operator_user_id FROM operation_logs WHERE action_type = 'TEST_ACTION'",
                Long.class
        ));
    }

    @Test
    void publicAttendanceSubmissionDoesNotBecomePermanentDeletionHistory() {
        long targetId = insertMember("delete-submission", "公开签到历史成员");
        jdbc.update("""
                INSERT INTO public_attendance_submissions (
                  request_id, student_no, record_id, action, name, submitted_at,
                  review_status, message
                ) VALUES (
                  'delete-history-submission', 'delete-submission', 900001,
                  'CHECK_IN', '公开签到历史成员', datetime('now', 'localtime'),
                  'PENDING', '测试提交'
                )
                """);

        users.delete(targetId, "清理只有短期签到回执的账号");

        assertEquals(0, userCount(targetId));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM public_attendance_submissions
                WHERE request_id = 'delete-history-submission'
                """, Integer.class));
    }

    @Test
    void businessAuthorshipPreventsPhysicalDeletion() {
        long targetId = insertMember("delete-author", "业务留痕成员");
        insertTrainingSession("成员删除保护-业务创建", targetId);

        assertDeletionBlocked(targetId, "培训记录");
    }

    @Test
    void emptyAccountCanBeDeletedAndLeavesTrustworthyAuditLog() {
        long targetId = insertMember("delete-empty", "空白测试账号");

        users.delete(targetId, "清理从未使用的测试账号");

        assertEquals(0, userCount(targetId));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM operation_logs
                WHERE action_type = 'DELETE_USER'
                  AND target_type = 'users'
                  AND target_id = ?
                  AND before_data LIKE '%delete-empty%'
                  AND reason LIKE '清理从未使用的测试账号%'
                """, Integer.class, targetId));
    }

    @Test
    void disablingLinkedMemberPreservesTrainingHistory() {
        long targetId = insertMember("delete-disable", "停用历史成员");
        long sessionId = insertTrainingSession("成员删除保护-停用保留", adminId);
        jdbc.update("""
                INSERT INTO training_participants (
                  session_id, user_id, student_no_snapshot, name_snapshot, duration_hours
                ) VALUES (?, ?, 'delete-disable', '停用历史成员', 3)
                """, sessionId, targetId);

        users.update(targetId, new UserService.UpdateUserRequest(
                "停用历史成员",
                "MEMBER",
                "DISABLED",
                null,
                null,
                null,
                null,
                "毕业停用"
        ));

        assertEquals("DISABLED", jdbc.queryForObject(
                "SELECT status FROM users WHERE id = ?", String.class, targetId));
        assertEquals(targetId, jdbc.queryForObject(
                "SELECT user_id FROM training_participants WHERE session_id = ?",
                Long.class,
                sessionId
        ));
        assertEquals(3, jdbc.queryForObject(
                "SELECT duration_hours FROM training_participants WHERE session_id = ?",
                Integer.class,
                sessionId
        ));
    }

    @Test
    void concurrentHistoryWriteCommitsBeforeDeletionCheck() throws Exception {
        long targetId = insertMember("delete-concurrent", "并发历史成员");
        long sessionId = insertTrainingSession("成员删除保护-并发写入", adminId);
        String databaseUrl = "jdbc:sqlite:" + STORAGE_ROOT.resolve("data/attendance.db").toString().replace('\\', '/');
        AuthUser admin = new AuthUser(
                adminId,
                "delete-admin",
                "删除保护测试管理员",
                Role.ADMIN,
                Instant.now().plusSeconds(3600)
        );

        try (Connection writer = DriverManager.getConnection(databaseUrl)) {
            writer.setAutoCommit(false);
            try (PreparedStatement statement = writer.prepareStatement("""
                    INSERT INTO training_participants (
                      session_id, user_id, student_no_snapshot, name_snapshot, duration_hours
                    ) VALUES (?, ?, 'delete-concurrent', '并发历史成员', 1)
                    """)) {
                statement.setLong(1, sessionId);
                statement.setLong(2, targetId);
                statement.executeUpdate();
            }

            CountDownLatch deletionStarted = new CountDownLatch(1);
            CompletableFuture<Void> deletion = CompletableFuture.runAsync(() -> {
                AuthContext.set(admin);
                try {
                    deletionStarted.countDown();
                    users.delete(targetId, "并发删除保护测试");
                } finally {
                    AuthContext.clear();
                }
            });

            assertTrue(deletionStarted.await(2, TimeUnit.SECONDS), "删除线程未开始执行");
            assertThrows(TimeoutException.class,
                    () -> deletion.get(250, TimeUnit.MILLISECONDS),
                    "删除应等待正在提交的历史写入");
            writer.commit();

            CompletionException exception = assertThrows(
                    CompletionException.class,
                    () -> deletion.orTimeout(5, TimeUnit.SECONDS).join()
            );
            assertTrue(exception.getCause() instanceof ApiException);
            ApiException apiException = (ApiException) exception.getCause();
            assertEquals(HttpStatus.CONFLICT, apiException.status());
            assertTrue(apiException.getMessage().contains("培训记录"));
        }

        assertEquals(1, userCount(targetId));
        assertEquals(targetId, jdbc.queryForObject(
                "SELECT user_id FROM training_participants WHERE session_id = ?",
                Long.class,
                sessionId
        ));
    }

    private void assertDeletionBlocked(long targetId, String expectedReference) {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> users.delete(targetId, "尝试删除有关联成员")
        );

        assertEquals(HttpStatus.CONFLICT, exception.status());
        assertTrue(exception.getMessage().contains(expectedReference));
        assertTrue(exception.getMessage().contains("停用账号"));
        assertEquals(1, userCount(targetId));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM operation_logs
                WHERE action_type = 'DELETE_USER' AND target_id = ?
                """, Integer.class, targetId));
    }

    private long insertMember(String studentNo, String name) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES (?, ?, 'test-hash', 'MEMBER', 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name);
        if (id == null) {
            throw new IllegalStateException("测试成员创建失败");
        }
        return id;
    }

    private long insertTrainingSession(String title, long actorId) {
        Long id = jdbc.queryForObject("""
                INSERT INTO training_sessions (
                  title, training_date, status, created_by, updated_by
                ) VALUES (?, '2026-08-13', 'COMPLETED', ?, ?)
                RETURNING id
                """, Long.class, title, actorId, actorId);
        if (id == null) {
            throw new IllegalStateException("测试培训创建失败");
        }
        return id;
    }

    private int userCount(long id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class,
                id
        );
        return count == null ? 0 : count;
    }

    private long ensureAdmin() {
        Long existing = jdbc.query("""
                SELECT id FROM users WHERE student_no = 'delete-admin'
                """, resultSet -> resultSet.next() ? resultSet.getLong(1) : null);
        if (existing != null) {
            return existing;
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('delete-admin', '删除保护测试管理员', 'test-hash', 'ADMIN', 'ACTIVE', 0)
                RETURNING id
                """, Long.class);
        if (id == null) {
            throw new IllegalStateException("测试管理员创建失败");
        }
        return id;
    }

    private void cleanTestData() {
        jdbc.update("DELETE FROM operation_logs");
        jdbc.update("DELETE FROM public_attendance_submissions WHERE student_no LIKE 'delete-%'");
        jdbc.update("DELETE FROM attendance_records WHERE student_no_snapshot LIKE 'delete-%'");
        jdbc.update("DELETE FROM training_sessions WHERE title LIKE '成员删除保护-%'");
        jdbc.update("DELETE FROM duty_schedule_slots WHERE note = '成员删除保护-固定周表'");
        jdbc.update("DELETE FROM repair_cases WHERE remark = '成员删除保护-维修'");
        jdbc.update("DELETE FROM app_settings WHERE setting_key LIKE 'delete.history.%'");
        jdbc.update("""
                UPDATE users
                SET disabled_by = NULL, created_by = NULL, updated_by = NULL
                WHERE student_no LIKE 'delete-%'
                """);
        jdbc.update("DELETE FROM users WHERE student_no LIKE 'delete-%' AND student_no <> 'delete-admin'");
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("ca-user-delete-history-test-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
