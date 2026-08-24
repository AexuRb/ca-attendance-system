package com.ca.attendance.maintenance;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.config.DatabaseMigrator;
import com.ca.attendance.config.SQLiteDataSourceConfiguration;
import com.ca.attendance.config.StoragePaths;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupRecoveryIntegrationTest {
    private static final TypeReference<List<LinkedHashMap<String, Object>>> ROWS_TYPE = new TypeReference<>() {
    };

    @TempDir
    Path tempDirectory;

    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private ObjectMapper objectMapper;
    private StoragePaths storagePaths;
    private TokenService tokens;
    private CountingTransactionManager transactionManager;
    private BackupService backups;
    private long adminId;
    private long memberId;

    @BeforeEach
    void setUp() throws Exception {
        storagePaths = new StoragePaths(tempDirectory.toString());
        dataSource = (HikariDataSource) new SQLiteDataSourceConfiguration().dataSource(storagePaths);
        new DatabaseMigrator(dataSource).run();
        jdbc = new JdbcTemplate(dataSource);
        objectMapper = new ObjectMapper();
        tokens = new TokenService(12);
        transactionManager = new CountingTransactionManager(dataSource);
        backups = new BackupService(
                jdbc,
                objectMapper,
                new TransactionTemplate(transactionManager),
                tokens,
                storagePaths
        );

        adminId = requiredId(jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('9900000001', '测试管理员', 'test-hash', 'ADMIN', 'ACTIVE', 0)
                RETURNING id
                """, Long.class));
        memberId = requiredId(jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('2026000001', '演练成员', 'test-hash', 'MEMBER', 'ACTIVE', 0)
                RETURNING id
                """, Long.class));
        AuthContext.set(new AuthUser(
                adminId,
                "9900000001",
                "测试管理员",
                Role.ADMIN,
                Instant.now().plusSeconds(3600)
        ));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void restoreUsesOnePhysicalTransactionAndRevokesExistingTokens() throws Exception {
        BackupService.BackupItem source = backups.create();
        String token = tokens.issue(adminId, "9900000001", "测试管理员", Role.ADMIN);
        transactionManager.resetBeginCount();

        BackupService.RestoreResult result = backups.restore(upload(source));

        assertEquals(1, transactionManager.beginCount());
        assertThrows(ApiException.class, () -> tokens.require(token));
        assertTrue(Files.isRegularFile(storagePaths.backupDirectory().resolve(result.safetyBackup().filename())));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = 'RESTORE_BACKUP'",
                Integer.class
        ));
    }

    @Test
    void restoresEverySupportedTableAndPreservesRelationships() throws Exception {
        seedEveryBusinessTable();
        Map<String, Integer> expectedCounts = tableCounts();
        BackupService.BackupItem source = backups.create();

        mutateEverySeededArea();
        backups.restore(upload(source));

        for (Map.Entry<String, Integer> entry : expectedCounts.entrySet()) {
            int expected = entry.getValue() + ("operation_logs".equals(entry.getKey()) ? 1 : 0);
            assertEquals(expected, count(entry.getKey()), "恢复后表数量不一致：" + entry.getKey());
        }
        assertEquals("演练成员", jdbc.queryForObject(
                "SELECT name FROM users WHERE student_no = '2026000001'", String.class));
        assertEquals("数据安全演练培训", jdbc.queryForObject(
                "SELECT title FROM training_sessions WHERE id = 301", String.class));
        assertEquals(memberId, jdbc.queryForObject(
                "SELECT user_id FROM training_participants WHERE id = 302", Long.class));
        assertEquals("LEAVE", jdbc.queryForObject(
                "SELECT attendance_status FROM training_participants WHERE id = 302", String.class));
        assertEquals(401L, jdbc.queryForObject(
                "SELECT slot_id FROM duty_schedule_assignees WHERE id = 402", Long.class));
        assertEquals("JXWX20260810-0007", jdbc.queryForObject(
                "SELECT case_no FROM repair_cases WHERE id = 501", String.class));
        assertEquals(7, jdbc.queryForObject(
                "SELECT last_value FROM repair_case_sequences WHERE sequence_date = '20260810'", Integer.class));
        assertEquals("演练策略值", jdbc.queryForObject(
                "SELECT setting_value FROM app_settings WHERE setting_key = 'recovery.drill.marker'", String.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT enabled FROM duty_weekday_settings WHERE weekday = 3", Integer.class));
        assertEquals(601L, jdbc.queryForObject(
                "SELECT record_id FROM public_attendance_submissions WHERE request_id = 'recovery-drill-request'",
                Long.class
        ));
        assertEquals("ok", jdbc.queryForObject("PRAGMA integrity_check", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pragma_foreign_key_check", Integer.class));
    }

    @Test
    void everyGeneratedBackupCanBeReadByTheSameVersion() throws Exception {
        String largeReason = "可恢复性边界数据".repeat(1024 * 1024);
        jdbc.update("""
                INSERT INTO operation_logs (
                  operator_user_id, operator_student_no, operator_name, action_type, target_type, reason
                ) VALUES (?, '9900000001', '测试管理员', 'BACKUP_CONTRACT_TEST', 'maintenance_backups', ?)
                """, adminId, largeReason);

        BackupService.BackupItem source = backups.create();

        assertDoesNotThrow(() -> backups.restore(upload(source)));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = 'BACKUP_CONTRACT_TEST'",
                Integer.class
        ));
    }

    @Test
    void legacyBackupPreservesOptionalTablesItDoesNotContain() throws Exception {
        seedEveryBusinessTable();
        jdbc.update("UPDATE repair_case_sequences SET updated_at = '2000-01-01 00:00:00'");
        BackupService.BackupItem source = backups.create();
        byte[] legacyArchive = removeOptionalTables(Files.readAllBytes(backupPath(source)));
        Map<String, Integer> optionalCounts = new LinkedHashMap<>();
        BackupSchema.OPTIONAL_RESTORE_TABLES.forEach(table -> optionalCounts.put(table, count(table)));
        jdbc.update("UPDATE users SET name = '已被篡改' WHERE id = ?", memberId);

        backups.restore(new MockMultipartFile(
                "file", "legacy-backup.zip", "application/zip", legacyArchive
        ));

        assertEquals("演练成员", jdbc.queryForObject(
                "SELECT name FROM users WHERE id = ?", String.class, memberId));
        optionalCounts.forEach((table, expected) ->
                assertEquals(expected, count(table), "旧备份不应清空未包含的表：" + table));
        assertEquals("数据安全演练培训", jdbc.queryForObject(
                "SELECT title FROM training_sessions WHERE id = 301", String.class));
        assertEquals("JXWX20260810-0007", jdbc.queryForObject(
                "SELECT case_no FROM repair_cases WHERE id = 501", String.class));
        assertEquals("2000-01-01 00:00:00", jdbc.queryForObject(
                "SELECT updated_at FROM repair_case_sequences WHERE sequence_date = '20260810'", String.class));
    }

    @Test
    void malformedDateIsRejectedBeforeCreatingSafetyBackup() throws Exception {
        seedEveryBusinessTable();
        BackupService.BackupItem source = backups.create();
        byte[] malformedArchive = rewriteTrainingDate(
                Files.readAllBytes(backupPath(source)),
                "not-a-date"
        );
        int backupsBeforeRestore = backups.list().size();

        ApiException error = assertThrows(ApiException.class, () -> backups.restore(new MockMultipartFile(
                "file", "malformed-date.zip", "application/zip", malformedArchive
        )));

        assertTrue(error.getMessage().contains("training_sessions"));
        assertEquals(backupsBeforeRestore, backups.list().size());
        assertEquals("演练成员", jdbc.queryForObject(
                "SELECT name FROM users WHERE id = ?", String.class, memberId));
    }

    @Test
    void failedRestoreRollsBackCurrentDatabaseAndKeepsItsSafetyBackup() throws Exception {
        BackupService.BackupItem source = backups.create();
        byte[] invalidArchive = rewriteUserRole(Files.readAllBytes(backupPath(source)), "UNSUPPORTED_ROLE");
        jdbc.update("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('current-marker', '当前数据库标记', 'test-hash', 'MEMBER', 'ACTIVE', 0)
                """);
        String token = tokens.issue(adminId, "9900000001", "测试管理员", Role.ADMIN);
        int backupsBeforeRestore = backups.list().size();

        assertThrows(ApiException.class, () -> backups.restore(new MockMultipartFile(
                "file", "invalid-data.zip", "application/zip", invalidArchive
        )));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE student_no = 'current-marker'", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = 'RESTORE_BACKUP'", Integer.class));
        assertEquals(backupsBeforeRestore + 1, backups.list().size());
        assertNotNull(tokens.require(token));
        assertEquals("ok", jdbc.queryForObject("PRAGMA integrity_check", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pragma_foreign_key_check", Integer.class));
    }

    @Test
    void corruptArchiveDoesNotTouchCurrentDataOrCreateSafetyBackup() {
        int usersBefore = count("users");

        assertThrows(ApiException.class, () -> backups.restore(new MockMultipartFile(
                "file",
                "corrupt.zip",
                "application/zip",
                "not-a-zip".getBytes(StandardCharsets.UTF_8)
        )));

        assertEquals(usersBefore, count("users"));
        assertEquals(0, backups.list().size());
    }

    @Test
    void databaseWriteLockDoesNotLeaveAPartialRestore() throws Exception {
        BackupService.BackupItem source = backups.create();
        jdbc.execute("PRAGMA busy_timeout = 100");
        int usersBefore = count("users");
        int backupsBeforeRestore = backups.list().size();
        String databaseUrl = "jdbc:sqlite:" + storagePaths.databaseFile().toString().replace('\\', '/');

        try (Connection lockConnection = DriverManager.getConnection(databaseUrl);
             Statement lock = lockConnection.createStatement()) {
            lock.execute("PRAGMA busy_timeout = 100");
            lock.execute("BEGIN IMMEDIATE");

            assertThrows(ApiException.class, () -> backups.restore(upload(source)));

            assertEquals(usersBefore, count("users"));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM operation_logs WHERE action_type = 'RESTORE_BACKUP'", Integer.class));
            assertEquals(backupsBeforeRestore + 1, backups.list().size());
            assertEquals("ok", jdbc.queryForObject("PRAGMA integrity_check", String.class));
            lock.execute("ROLLBACK");
        }
    }

    private void seedEveryBusinessTable() {
        jdbc.update("""
                INSERT INTO training_sessions (
                  id, title, training_date, start_time, end_time, location, speaker, description,
                  status, created_by, updated_by
                ) VALUES (301, '数据安全演练培训', '2026-08-10', '09:00:00', '11:00:00',
                          '协会办公室', '测试管理员', '用于恢复演练', 'COMPLETED', ?, ?)
                """, adminId, adminId);
        jdbc.update("""
                INSERT INTO training_participants (
                  id, session_id, user_id, student_no_snapshot, name_snapshot, attendance_status,
                  duration_hours, remark, source, created_by, updated_by
                ) VALUES (302, 301, ?, '2026000001', '演练成员', 'LEAVE', 2, '已完成', 'MANUAL', ?, ?)
                """, memberId, adminId, adminId);
        jdbc.update("""
                INSERT INTO duty_schedule_slots (
                  id, weekday, start_time, end_time, title, location, note, enabled, status, created_by, updated_by
                ) VALUES (401, 3, '14:00:00', '16:00:00', '值班', '协会办公室', '演练排班', 1, 'ACTIVE', ?, ?)
                """, adminId, adminId);
        jdbc.update("""
                INSERT INTO duty_schedule_assignees (
                  id, slot_id, user_id, student_no_snapshot, name_snapshot, sort_order
                ) VALUES (402, 401, ?, '2026000001', '演练成员', 0)
                """, memberId);
        jdbc.update("""
                INSERT INTO repair_case_sequences (sequence_date, last_value)
                VALUES ('20260810', 7)
                """);
        jdbc.update("""
                INSERT INTO repair_cases (
                  id, case_no, agreement_type, owner_name, owner_phone, device_type, device_brand,
                  device_model, accessories, fault_description, service_description,
                  data_backup_confirmed, risk_acknowledged, privacy_acknowledged, status,
                  received_at, handler_user_id, handler_name_snapshot, remark, created_by, updated_by
                ) VALUES (501, 'JXWX20260810-0007', 'PERSONAL_DEVICE', '演练委托人', '13800000000',
                          '笔记本电脑', '测试品牌', '测试型号', '电源适配器', '无法开机', '检查供电',
                          1, 1, 1, 'REPAIRING', '2026-08-10 12:00:00', ?, '演练成员', '恢复演练', ?, ?)
                """, memberId, adminId, adminId);
        jdbc.update("""
                INSERT INTO attendance_records (
                  id, user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                  is_duty_day, within_duty_period, require_duty_day, require_duty_period,
                  check_in_time, check_out_time, check_in_status, check_out_status,
                  check_in_reviewed_by, check_out_reviewed_by, duration_minutes, valid_hours,
                  effective_status, source, created_by, updated_by
                ) VALUES (601, ?, '2026000001', '演练成员', '2026-08-10', 1,
                          1, 1, 1, 1, '2026-08-10 14:00:00', '2026-08-10 16:00:00',
                          'APPROVED', 'APPROVED', ?, ?, 120, 2, 'VALID', 'PUBLIC', ?, ?)
                """, memberId, adminId, adminId, adminId, adminId);
        jdbc.update("""
                INSERT INTO public_attendance_submissions (
                  request_id, student_no, record_id, action, name, submitted_at, review_status, message
                ) VALUES ('recovery-drill-request', '2026000001', 601, 'CHECK_OUT', '演练成员',
                          '2026-08-10 16:00:00', 'APPROVED', '签退成功')
                """);
        jdbc.update("""
                INSERT INTO operation_logs (
                  id, operator_user_id, operator_student_no, operator_name, action_type, target_type,
                  target_id, after_data, reason
                ) VALUES (701, ?, '9900000001', '测试管理员', 'RECOVERY_DRILL_MARKER',
                          'maintenance_backups', 1, '{"marker":true}', '数据安全演练')
                """, adminId);
        jdbc.update("""
                INSERT INTO app_settings (setting_key, setting_value, description, updated_by)
                VALUES ('recovery.drill.marker', '演练策略值', '数据安全演练', ?)
                ON CONFLICT(setting_key) DO UPDATE SET
                  setting_value = excluded.setting_value,
                  description = excluded.description,
                  updated_by = excluded.updated_by
                """, adminId);
        jdbc.update("""
                UPDATE duty_weekday_settings
                SET enabled = 0, updated_by = ?
                WHERE weekday = 3
                """, adminId);
    }

    private void mutateEverySeededArea() {
        jdbc.update("DELETE FROM public_attendance_submissions WHERE request_id = 'recovery-drill-request'");
        jdbc.update("DELETE FROM operation_logs WHERE id = 701");
        jdbc.update("DELETE FROM duty_schedule_assignees WHERE id = 402");
        jdbc.update("DELETE FROM duty_schedule_slots WHERE id = 401");
        jdbc.update("DELETE FROM training_participants WHERE id = 302");
        jdbc.update("DELETE FROM training_sessions WHERE id = 301");
        jdbc.update("DELETE FROM repair_cases WHERE id = 501");
        jdbc.update("DELETE FROM repair_case_sequences WHERE sequence_date = '20260810'");
        jdbc.update("DELETE FROM attendance_records WHERE id = 601");
        jdbc.update("DELETE FROM app_settings WHERE setting_key = 'recovery.drill.marker'");
        jdbc.update("UPDATE duty_weekday_settings SET enabled = 1 WHERE weekday = 3");
        jdbc.update("UPDATE users SET name = '已被篡改' WHERE id = ?", memberId);
    }

    private Map<String, Integer> tableCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        BackupSchema.RESTORE_TABLE_ORDER.forEach(table -> counts.put(table, count(table)));
        return counts;
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private MockMultipartFile upload(BackupService.BackupItem backup) throws Exception {
        return new MockMultipartFile(
                "file",
                backup.filename(),
                "application/zip",
                Files.readAllBytes(backupPath(backup))
        );
    }

    private Path backupPath(BackupService.BackupItem backup) {
        return storagePaths.backupDirectory().resolve(backup.filename());
    }

    private byte[] rewriteUserRole(byte[] source, String role) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(source), StandardCharsets.UTF_8);
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                byte[] bytes = input.readAllBytes();
                if ("users.json".equals(entry.getName())) {
                    List<LinkedHashMap<String, Object>> users = objectMapper.readValue(bytes, ROWS_TYPE);
                    users.getFirst().put("role", role);
                    bytes = objectMapper.writeValueAsBytes(users);
                }
                zip.putNextEntry(new ZipEntry(entry.getName()));
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private byte[] removeOptionalTables(byte[] source) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(source), StandardCharsets.UTF_8);
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] bytes = input.readAllBytes();
                if (name.endsWith(".json")
                        && !"metadata.json".equals(name)
                        && BackupSchema.OPTIONAL_RESTORE_TABLES.contains(name.substring(0, name.length() - 5))) {
                    continue;
                }
                if ("metadata.json".equals(name)) {
                    Map<String, Object> metadata = objectMapper.readValue(bytes, new TypeReference<>() {
                    });
                    metadata.remove("schemaVersion");
                    metadata.put("tables", List.of(
                            "users", "attendance_records", "operation_logs", "duty_weekday_settings"
                    ));
                    bytes = objectMapper.writeValueAsBytes(metadata);
                }
                zip.putNextEntry(new ZipEntry(name));
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private byte[] rewriteTrainingDate(byte[] source, String trainingDate) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(source), StandardCharsets.UTF_8);
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                byte[] bytes = input.readAllBytes();
                if ("training_sessions.json".equals(entry.getName())) {
                    List<LinkedHashMap<String, Object>> sessions = objectMapper.readValue(bytes, ROWS_TYPE);
                    sessions.getFirst().put("training_date", trainingDate);
                    bytes = objectMapper.writeValueAsBytes(sessions);
                }
                zip.putNextEntry(new ZipEntry(entry.getName()));
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private long requiredId(Long id) {
        assertNotNull(id);
        return id;
    }

    private static final class CountingTransactionManager extends DataSourceTransactionManager {
        private int beginCount;

        private CountingTransactionManager(HikariDataSource dataSource) {
            super(dataSource);
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            super.doBegin(transaction, definition);
            beginCount++;
        }

        private int beginCount() {
            return beginCount;
        }

        private void resetBeginCount() {
            beginCount = 0;
        }
    }
}
