package com.ca.attendance.config;

import com.ca.attendance.attendance.AttendanceRecord;
import com.ca.attendance.attendance.AttendanceRepository;
import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.maintenance.BackupService;
import com.ca.attendance.repair.RepairCaseItem;
import com.ca.attendance.repair.RepairCaseService;
import com.ca.attendance.schedule.DutyScheduleService;
import com.ca.attendance.schedule.DutyScheduleSlotItem;
import com.ca.attendance.settings.DutyPeriodService;
import com.ca.attendance.training.TrainingParticipantItem;
import com.ca.attendance.training.TrainingService;
import com.ca.attendance.training.TrainingSessionItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteDatabaseIntegrationTest {
    @TempDir
    Path tempDirectory;

    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private ObjectMapper objectMapper;
    private long adminId;

    @BeforeEach
    void setUp() throws Exception {
        StoragePaths storagePaths = new StoragePaths(tempDirectory.toString());
        DataSource configured = new SQLiteDataSourceConfiguration().dataSource(storagePaths);
        dataSource = (HikariDataSource) configured;
        new DatabaseMigrator(dataSource).run();
        jdbc = new JdbcTemplate(dataSource);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        adminId = requiredId(jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('admin', '管理员', 'test-hash', 'ADMIN', 'ACTIVE', 0)
                RETURNING id
                """, Long.class));
        jdbc.update("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('20240001', '测试成员', 'test-hash', 'MEMBER', 'ACTIVE', 0)
                """);
        AuthContext.set(new AuthUser(adminId, "admin", "管理员", Role.ADMIN, Instant.now().plusSeconds(3600)));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void createsVersionedDatabaseWithRequiredPragmas() {
        assertTrue(Files.isRegularFile(tempDirectory.resolve("data").resolve("attendance.db")));
        assertEquals(2, jdbc.queryForObject("PRAGMA user_version", Integer.class));
        assertEquals(1, jdbc.queryForObject("PRAGMA foreign_keys", Integer.class));
        assertEquals("wal", jdbc.queryForObject("PRAGMA journal_mode", String.class));
        assertEquals("ok", jdbc.queryForObject("PRAGMA quick_check", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pragma_foreign_key_check", Integer.class));
        assertEquals(7, jdbc.queryForObject("SELECT COUNT(*) FROM duty_weekday_settings", Integer.class));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pragma_table_info('repair_cases')
                WHERE name IN ('deleted_at', 'deleted_by')
                """, Integer.class));
        String createdAt = jdbc.queryForObject("SELECT created_at FROM users WHERE id = ?", String.class, adminId);
        LocalDateTime localCreatedAt = LocalDateTime.parse(createdAt.replace('T', ' '),
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        assertTrue(Duration.between(localCreatedAt, LocalDateTime.now()).abs().toMinutes() < 2);
    }

    @Test
    void migratesVersionOneRepairRowsWithoutDataLoss() throws Exception {
        StoragePaths legacyPaths = new StoragePaths(tempDirectory.resolve("legacy-v1").toString());
        try (HikariDataSource legacyDataSource = (HikariDataSource) new SQLiteDataSourceConfiguration().dataSource(legacyPaths)) {
            try (Connection connection = legacyDataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/sqlite/V1__initial_schema.sql"));
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA user_version = 1");
                }
            }

            JdbcTemplate legacyJdbc = new JdbcTemplate(legacyDataSource);
            legacyJdbc.update("""
                    INSERT INTO repair_cases (
                      case_no, agreement_type, owner_name, device_type, fault_description, status, received_at
                    )
                    VALUES ('JXWX-LEGACY-0001', 'PERSONAL_DEVICE', '旧版送修人', '笔记本电脑', '旧版故障', 'REPAIRING', datetime('now', 'localtime'))
                    """);

            new DatabaseMigrator(legacyDataSource).run();

            assertEquals(2, legacyJdbc.queryForObject("PRAGMA user_version", Integer.class));
            assertEquals(1, legacyJdbc.queryForObject(
                    "SELECT COUNT(*) FROM repair_cases WHERE case_no = 'JXWX-LEGACY-0001' AND deleted_at IS NULL",
                    Integer.class
            ));
            assertEquals(2, legacyJdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM pragma_table_info('repair_cases')
                    WHERE name IN ('deleted_at', 'deleted_by')
                    """, Integer.class));
        }
    }

    @Test
    void supportsCoreBusinessWritesAndGeneratedIds() {
        long memberId = requiredId(jdbc.queryForObject(
                "SELECT id FROM users WHERE student_no = '20240001'", Long.class));
        LocalDate today = LocalDate.now();

        AttendanceRepository attendance = new AttendanceRepository(jdbc);
        long attendanceId = attendance.insertCheckIn(
                memberId,
                "20240001",
                "测试成员",
                today,
                today.getDayOfWeek().getValue(),
                true,
                Timestamp.valueOf(LocalDateTime.of(today, LocalTime.of(14, 0))),
                "PENDING",
                "PENDING"
        );
        AttendanceRecord attendanceRecord = attendance.findById(attendanceId).orElseThrow();
        assertEquals(today, attendanceRecord.dutyDate());
        assertEquals(LocalTime.of(14, 0), attendanceRecord.checkInTime().toLocalTime());

        OperationLogService logs = new OperationLogService(jdbc, objectMapper);
        DutyPeriodService periods = new DutyPeriodService(jdbc, objectMapper, logs);
        periods.update(List.of(new DutyPeriodService.DutyPeriodRequest("14:00", "16:00")));

        DutyScheduleService schedules = new DutyScheduleService(jdbc, logs, periods);
        DutyScheduleSlotItem slot = schedules.create(new DutyScheduleService.SlotRequest(
                1,
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                "值班",
                "协会办公室",
                null,
                true,
                List.of(new DutyScheduleService.AssigneeRequest("20240001", null))
        ));
        assertTrue(slot.id() > 0);
        assertEquals(1, slot.assignees().size());
        assertEquals("14:00:00", jdbc.queryForObject(
                "SELECT start_time FROM duty_schedule_slots WHERE id = ?", String.class, slot.id()));

        TrainingService trainings = new TrainingService(jdbc, logs);
        TrainingSessionItem session = trainings.create(new TrainingService.SessionRequest(
                "离线系统培训",
                today,
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                "协会办公室",
                "管理员",
                "SQLite 集成测试",
                null
        ));
        TrainingParticipantItem participant = trainings.addParticipant(
                session.id(),
                new TrainingService.ParticipantRequest("20240001", "测试成员", new BigDecimal("2.00"), null, null)
        );
        assertTrue(session.id() > 0);
        assertEquals(new BigDecimal("2"), participant.durationHours().stripTrailingZeros());
        assertEquals(today.toString(), jdbc.queryForObject(
                "SELECT training_date FROM training_sessions WHERE id = ?", String.class, session.id()));
        assertEquals("14:00:00", jdbc.queryForObject(
                "SELECT start_time FROM training_sessions WHERE id = ?", String.class, session.id()));
        assertEquals(1, trainings.list("离线系统培训", null, today, today).size());

        RepairCaseService repairs = new RepairCaseService(jdbc, logs, backupService());
        RepairCaseItem repair = repairs.create(new RepairCaseService.RepairCaseRequest(
                "PERSONAL_DEVICE",
                "送修同学",
                "13800000000",
                null,
                "笔记本电脑",
                "测试品牌",
                "测试型号",
                null,
                "电源适配器",
                "无法开机",
                null,
                true,
                true,
                true,
                "REPAIRING",
                LocalDateTime.now(),
                null,
                "管理员",
                null
        ));
        assertTrue(repair.id() > 0);
        assertNotNull(repair.caseNo());
    }

    @Test
    void protectsRepairRecycleBinAndBacksUpPermanentDeletion() {
        OperationLogService logs = new OperationLogService(jdbc, objectMapper);
        BackupService backups = backupService();
        RepairCaseService repairs = new RepairCaseService(jdbc, logs, backups);
        LocalDateTime receivedAt = LocalDateTime.now();
        RepairCaseItem repair = repairs.create(new RepairCaseService.RepairCaseRequest(
                "PERSONAL_DEVICE", "回收站测试", "13800000001", null,
                "笔记本电脑", "测试品牌", "测试型号", null, "电源适配器",
                "无法开机", null, true, true, true, "REPAIRING",
                receivedAt, null, "管理员", null
        ));

        long ministerId = requiredId(jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('minister', '测试部长', 'test-hash', 'MINISTER', 'ACTIVE', 0)
                RETURNING id
                """, Long.class));
        long presidentId = requiredId(jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('president', '测试会长', 'test-hash', 'PRESIDENT', 'ACTIVE', 0)
                RETURNING id
                """, Long.class));

        AuthContext.set(new AuthUser(ministerId, "minister", "测试部长", Role.MINISTER, Instant.now().plusSeconds(3600)));
        assertThrows(ApiException.class, () -> repairs.moveToRecycleBin(repair.id()));

        AuthContext.set(new AuthUser(presidentId, "president", "测试会长", Role.PRESIDENT, Instant.now().plusSeconds(3600)));
        RepairCaseItem deleted = repairs.moveToRecycleBin(repair.id());
        assertNotNull(deleted.deletedAt());
        assertEquals("测试会长", deleted.deletedByName());
        assertEquals(0, repairs.list(null, "ALL", receivedAt.toLocalDate(), receivedAt.toLocalDate()).size());
        assertThrows(ApiException.class, repairs::recycleBin);

        AuthContext.set(new AuthUser(adminId, "admin", "管理员", Role.ADMIN, Instant.now().plusSeconds(3600)));
        assertEquals(1, repairs.recycleBin().size());
        RepairCaseItem restored = repairs.restore(repair.id());
        assertNull(restored.deletedAt());
        assertEquals(1, repairs.list(null, "ALL", receivedAt.toLocalDate(), receivedAt.toLocalDate()).size());

        repairs.moveToRecycleBin(repair.id());
        assertThrows(ApiException.class, () -> repairs.purge(repair.id(), "WRONG-CASE-NO"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM repair_cases WHERE id = ?", Integer.class, repair.id()));

        RepairCaseService.PurgeResult result = repairs.purge(repair.id(), repair.caseNo());
        assertEquals(repair.caseNo(), result.caseNo());
        assertTrue(Files.isRegularFile(tempDirectory.resolve("backups").resolve("app").resolve(result.safetyBackup().filename())));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM repair_cases WHERE id = ?", Integer.class, repair.id()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = 'PURGE_REPAIR_CASE' AND target_id = ?",
                Integer.class,
                repair.id()
        ));
    }

    @Test
    void createsAndRestoresPortableBackupOnSQLite() throws Exception {
        StoragePaths storagePaths = new StoragePaths(tempDirectory.toString());
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        BackupService backups = new BackupService(jdbc, objectMapper, transactions, new TokenService(12), storagePaths);

        LocalDate backupDate = LocalDate.now();
        jdbc.update("""
                INSERT INTO training_sessions (title, training_date, start_time, end_time, status)
                VALUES ('备份恢复培训', ?, '09:00:00', '10:00:00', 'PLANNED')
                """, backupDate.toString());

        BackupService.BackupItem backup = backups.create();
        Path backupPath = storagePaths.backupDirectory().resolve(backup.filename());
        assertTrue(Files.isRegularFile(backupPath));

        jdbc.update("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('temporary', '临时成员', 'test-hash', 'MEMBER', 'ACTIVE', 0)
                """);
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));

        MockMultipartFile upload = new MockMultipartFile(
                "file",
                backup.filename(),
                "application/zip",
                Files.readAllBytes(backupPath)
        );
        BackupService.RestoreResult result = backups.restore(upload);

        assertTrue(result.totalRows() >= 9);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE student_no = 'temporary'", Integer.class));
        assertEquals(backupDate.toString(), jdbc.queryForObject(
                "SELECT training_date FROM training_sessions WHERE title = '备份恢复培训'", String.class));
        assertEquals("09:00:00", jdbc.queryForObject(
                "SELECT start_time FROM training_sessions WHERE title = '备份恢复培训'", String.class));
        assertEquals("ok", jdbc.queryForObject("PRAGMA integrity_check", String.class));
    }

    private long requiredId(Long id) {
        assertNotNull(id);
        return id;
    }

    private BackupService backupService() {
        StoragePaths storagePaths = new StoragePaths(tempDirectory.toString());
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        return new BackupService(jdbc, objectMapper, transactions, new TokenService(12), storagePaths);
    }
}
