package com.ca.attendance.config;

import com.ca.attendance.attendance.AttendanceRecord;
import com.ca.attendance.attendance.AttendanceRepository;
import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.export.CustomExportService;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.maintenance.BackupService;
import com.ca.attendance.repair.RepairCaseItem;
import com.ca.attendance.repair.RepairCaseService;
import com.ca.attendance.schedule.DutyScheduleImportService;
import com.ca.attendance.schedule.DutyScheduleService;
import com.ca.attendance.schedule.DutyScheduleSlotItem;
import com.ca.attendance.settings.DutyPeriodService;
import com.ca.attendance.training.TrainingParticipantItem;
import com.ca.attendance.training.TrainingService;
import com.ca.attendance.training.TrainingSessionItem;
import com.ca.attendance.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(9, jdbc.queryForObject("PRAGMA user_version", Integer.class));
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
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pragma_table_info('attendance_records')
                WHERE name = 'within_duty_period'
                """, Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sqlite_master
                WHERE type = 'table' AND name = 'public_attendance_submissions'
                """, Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sqlite_master
                WHERE type = 'table' AND name = 'repair_case_sequences'
                """, Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM sqlite_master
                WHERE type = 'table' AND name IN (
                  'duty_schedule_exceptions',
                  'duty_schedule_exception_assignees',
                  'duty_shift_reassignments'
                )
                """, Integer.class));
        String createdAt = jdbc.queryForObject("SELECT created_at FROM users WHERE id = ?", String.class, adminId);
        LocalDateTime localCreatedAt = LocalDateTime.parse(createdAt.replace('T', ' '),
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        assertTrue(Duration.between(localCreatedAt, LocalDateTime.now()).abs().toMinutes() < 2);
    }

    @Test
    void versionFiveAutoApprovesExistingMinisterAttendanceOnly() throws Exception {
        long ministerId = requiredId(jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('minister-migration', '迁移测试部长', 'test-hash', 'MINISTER', 'ACTIVE', 0)
                RETURNING id
                """, Long.class));
        long memberId = requiredId(jdbc.queryForObject(
                "SELECT id FROM users WHERE student_no = '20240001'", Long.class));
        LocalDate today = LocalDate.now();
        LocalDateTime checkIn = today.atTime(14, 0);
        LocalDateTime checkOut = today.atTime(16, 0);
        long ministerRecordId = requiredId(jdbc.queryForObject("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                  is_duty_day, within_duty_period, check_in_time, check_out_time,
                  check_in_status, check_out_status, effective_status
                )
                VALUES (?, 'minister-migration', '迁移测试部长', ?, ?, 1, 1, ?, ?, 'PENDING', 'PENDING', 'PENDING')
                RETURNING id
                """, Long.class, ministerId, today, today.getDayOfWeek().getValue(), checkIn, checkOut));
        long memberRecordId = requiredId(jdbc.queryForObject("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                  is_duty_day, within_duty_period, check_in_time, check_out_time,
                  check_in_status, check_out_status, effective_status
                )
                VALUES (?, '20240001', '测试成员', ?, ?, 1, 1, ?, ?, 'PENDING', 'PENDING', 'PENDING')
                RETURNING id
                """, Long.class, memberId, today, today.getDayOfWeek().getValue(), checkIn, checkOut));
        jdbc.update("""
                INSERT INTO public_attendance_submissions (
                  request_id, student_no, record_id, action, name, submitted_at, review_status, message
                ) VALUES ('migration-receipt', 'minister-migration', ?, 'CHECK_OUT', '迁移测试部长', ?, 'PENDING', '签退提交成功')
                """, ministerRecordId, checkOut);
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/sqlite/V5__minister_attendance_auto_approval.sql"));
        }

        assertEquals("AUTO_APPROVED", jdbc.queryForObject(
                "SELECT check_in_status FROM attendance_records WHERE id = ?", String.class, ministerRecordId));
        assertEquals("AUTO_APPROVED", jdbc.queryForObject(
                "SELECT check_out_status FROM attendance_records WHERE id = ?", String.class, ministerRecordId));
        assertEquals("VALID", jdbc.queryForObject(
                "SELECT effective_status FROM attendance_records WHERE id = ?", String.class, ministerRecordId));
        assertEquals(2, jdbc.queryForObject(
                "SELECT valid_hours FROM attendance_records WHERE id = ?", Integer.class, ministerRecordId));
        assertEquals("AUTO_APPROVED", jdbc.queryForObject(
                "SELECT review_status FROM public_attendance_submissions WHERE request_id = 'migration-receipt'", String.class));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT check_in_status FROM attendance_records WHERE id = ?", String.class, memberRecordId));
    }

    @Test
    void versionNinePreservesReviewBasedEligibilityForLegacyRecords() throws Exception {
        StoragePaths legacyPaths = new StoragePaths(tempDirectory.resolve("legacy-v8-attendance").toString());
        try (HikariDataSource legacyDataSource = (HikariDataSource) new SQLiteDataSourceConfiguration().dataSource(legacyPaths)) {
            try (Connection connection = legacyDataSource.getConnection()) {
                for (String resource : List.of(
                        "db/sqlite/V1__initial_schema.sql",
                        "db/sqlite/V2__repair_recycle_bin.sql",
                        "db/sqlite/V3__attendance_duty_period.sql",
                        "db/sqlite/V4__public_submission_idempotency.sql",
                        "db/sqlite/V5__minister_attendance_auto_approval.sql",
                        "db/sqlite/V6__reserved.sql",
                        "db/sqlite/V7__remove_schedule_adjustments.sql",
                        "db/sqlite/V8__repair_case_sequences.sql"
                )) {
                    ScriptUtils.executeSqlScript(connection, new ClassPathResource(resource));
                }
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA user_version = 8");
                }
            }

            JdbcTemplate legacyJdbc = new JdbcTemplate(legacyDataSource);
            long memberId = requiredId(legacyJdbc.queryForObject("""
                    INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                    VALUES ('legacy-policy-member', '旧规则成员', 'test-hash', 'MEMBER', 'ACTIVE', 0)
                    RETURNING id
                    """, Long.class));
            LocalDate date = LocalDate.of(2026, 8, 10);
            long recordId = requiredId(legacyJdbc.queryForObject("""
                    INSERT INTO attendance_records (
                      user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                      is_duty_day, within_duty_period, check_in_time, check_out_time,
                      check_in_status, check_out_status, duration_minutes, valid_hours, effective_status
                    )
                    VALUES (?, 'legacy-policy-member', '旧规则成员', ?, 1, 0, 0, ?, ?,
                            'APPROVED', 'APPROVED', 0, 0, 'INVALID')
                    RETURNING id
                    """, Long.class, memberId, date, date.atTime(14, 0), date.atTime(16, 0)));

            new DatabaseMigrator(legacyDataSource).run();

            assertEquals(9, legacyJdbc.queryForObject("PRAGMA user_version", Integer.class));
            assertEquals(0, legacyJdbc.queryForObject(
                    "SELECT require_duty_day FROM attendance_records WHERE id = ?", Integer.class, recordId));
            assertEquals(0, legacyJdbc.queryForObject(
                    "SELECT require_duty_period FROM attendance_records WHERE id = ?", Integer.class, recordId));
            assertEquals("VALID", legacyJdbc.queryForObject(
                    "SELECT effective_status FROM attendance_records WHERE id = ?", String.class, recordId));
            assertEquals(2, legacyJdbc.queryForObject(
                    "SELECT valid_hours FROM attendance_records WHERE id = ?", Integer.class, recordId));
        }
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

            assertEquals(9, legacyJdbc.queryForObject("PRAGMA user_version", Integer.class));
            assertEquals(1, legacyJdbc.queryForObject(
                    "SELECT COUNT(*) FROM repair_cases WHERE case_no = 'JXWX-LEGACY-0001' AND deleted_at IS NULL",
                    Integer.class
            ));
            assertEquals(2, legacyJdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM pragma_table_info('repair_cases')
                    WHERE name IN ('deleted_at', 'deleted_by')
                    """, Integer.class));
            assertEquals(1, legacyJdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM pragma_table_info('attendance_records')
                    WHERE name = 'within_duty_period'
                    """, Integer.class));
            assertEquals(1, legacyJdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM sqlite_master
                    WHERE type = 'table' AND name = 'public_attendance_submissions'
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
                true,
                false,
                false,
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
        jdbc.update("UPDATE users SET role = 'MINISTER' WHERE id = ?", memberId);
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

        RepairCaseService repairs = new RepairCaseService(jdbc, logs, backupService(), new UserRepository(jdbc));
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
                adminId,
                "管理员",
                null
        ));
        assertTrue(repair.id() > 0);
        assertNotNull(repair.caseNo());
        assertEquals(adminId, repair.handlerUserId());
        assertEquals("管理员", repair.handlerName());
    }

    @Test
    void protectsRepairRecycleBinAndBacksUpPermanentDeletion() {
        OperationLogService logs = new OperationLogService(jdbc, objectMapper);
        BackupService backups = backupService();
        RepairCaseService repairs = new RepairCaseService(jdbc, logs, backups, new UserRepository(jdbc));
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
    void repairNumbersRemainMonotonicAfterPurgingAMiddleCase() {
        OperationLogService logs = new OperationLogService(jdbc, objectMapper);
        RepairCaseService repairs = new RepairCaseService(jdbc, logs, backupService(), new UserRepository(jdbc));

        RepairCaseItem first = createRepair(repairs, "编号测试一");
        RepairCaseItem second = createRepair(repairs, "编号测试二");
        RepairCaseItem third = createRepair(repairs, "编号测试三");
        repairs.moveToRecycleBin(second.id());
        repairs.purge(second.id(), second.caseNo());

        RepairCaseItem fourth = createRepair(repairs, "编号测试四");

        assertTrue(first.caseNo().endsWith("-0001"));
        assertTrue(second.caseNo().endsWith("-0002"));
        assertTrue(third.caseNo().endsWith("-0003"));
        assertTrue(fourth.caseNo().endsWith("-0004"));
    }

    @Test
    void previewsAndAtomicallyReplacesOnlyImportedScheduleGroups() throws Exception {
        OperationLogService logs = new OperationLogService(jdbc, objectMapper);
        DutyPeriodService periods = new DutyPeriodService(jdbc, objectMapper, logs);
        periods.update(List.of(
                new DutyPeriodService.DutyPeriodRequest("14:00", "16:00"),
                new DutyPeriodService.DutyPeriodRequest("16:00", "18:00")
        ));
        DutyScheduleService schedules = new DutyScheduleService(jdbc, logs, periods);
        DutyScheduleImportService imports = new DutyScheduleImportService(jdbc, logs, periods);

        jdbc.update("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES
                  ('20241001', '部长甲', 'test-hash', 'MINISTER', 'ACTIVE', 0),
                  ('20241002', '部长乙', 'test-hash', 'MINISTER', 'ACTIVE', 0),
                  ('20241003', '普通成员', 'test-hash', 'MEMBER', 'ACTIVE', 0)
                """);
        long importMinisterId = requiredId(jdbc.queryForObject(
                "SELECT id FROM users WHERE student_no = '20241001'", Long.class));
        AuthContext.set(new AuthUser(importMinisterId, "20241001", "部长甲", Role.MINISTER, Instant.now().plusSeconds(3600)));
        assertThrows(ApiException.class, imports::exportTemplate);
        AuthContext.set(new AuthUser(adminId, "admin", "管理员", Role.ADMIN, Instant.now().plusSeconds(3600)));

        DutyScheduleSlotItem mondayPrimary = schedules.create(new DutyScheduleService.SlotRequest(
                1, LocalTime.of(14, 0), LocalTime.of(16, 0), "原周一排班", null, null, true,
                List.of(new DutyScheduleService.AssigneeRequest("20241001", null))
        ));
        schedules.create(new DutyScheduleService.SlotRequest(
                1, LocalTime.of(14, 0), LocalTime.of(16, 0), "重复周一排班", null, null, true,
                List.of(new DutyScheduleService.AssigneeRequest("20241001", null))
        ));
        DutyScheduleSlotItem preservedTuesday = schedules.create(new DutyScheduleService.SlotRequest(
                2, LocalTime.of(16, 0), LocalTime.of(18, 0), "保留周二排班", null, null, true,
                List.of(new DutyScheduleService.AssigneeRequest("20241001", null))
        ));

        MockMultipartFile invalidFile = scheduleImportFile("invalid-schedule.xlsx", List.<String[]>of(
                new String[]{"星期一", "14:00-16:00", "20241002", "部长乙"},
                new String[]{"星期二", "16:00-18:00", "20241003", "普通成员"}
        ));
        DutyScheduleImportService.ImportPreview invalidPreview = imports.preview(invalidFile);
        assertFalse(invalidPreview.valid());
        assertTrue(invalidPreview.issues().stream().anyMatch(issue -> issue.message().contains("部长、会长或管理员")));
        assertThrows(ApiException.class, () -> imports.importSchedules(invalidFile));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM duty_schedule_slots WHERE status = 'ACTIVE'", Integer.class));

        DutyScheduleImportService.ExportFile template = imports.exportTemplate();
        assertTrue(template.bytes().length > 1000);
        assertEquals("PK", new String(template.bytes(), 0, 2, java.nio.charset.StandardCharsets.US_ASCII));

        MockMultipartFile validFile = scheduleImportFile("valid-schedule.xlsx", List.<String[]>of(
                new String[]{"星期一", "14:00-16:00", "20241001", "部长甲"},
                new String[]{"星期一", "14:00-16:00", "20241002", ""}
        ));
        DutyScheduleImportService.ImportPreview preview = imports.preview(validFile);
        assertTrue(preview.valid());
        assertEquals(1, preview.groupCount());
        assertEquals(2, preview.memberCount());

        DutyScheduleImportService.ImportResult result = imports.importSchedules(validFile);
        assertEquals(1, result.replacedGroups());
        assertEquals(2, result.assignedMembers());
        assertEquals(1, result.archivedDuplicateSlots());

        List<DutyScheduleSlotItem> activeSchedules = schedules.list();
        List<DutyScheduleSlotItem> mondaySlots = activeSchedules.stream()
                .filter(item -> item.weekday() == 1 && LocalTime.of(14, 0).equals(item.startTime()))
                .toList();
        assertEquals(1, mondaySlots.size());
        assertEquals(mondayPrimary.id(), mondaySlots.getFirst().id());
        assertEquals(List.of("20241001", "20241002"), mondaySlots.getFirst().assignees().stream()
                .map(DutyScheduleSlotItem.AssigneeItem::studentNo)
                .toList());
        assertTrue(activeSchedules.stream().anyMatch(item -> item.id() == preservedTuesday.id()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = 'IMPORT_DUTY_SCHEDULES'",
                Integer.class
        ));
    }

    @Test
    void customExportsUseRoleScopedSourcesAndRequestedFieldOrder() throws Exception {
        OperationLogService logs = new OperationLogService(jdbc, objectMapper);
        CustomExportService exports = new CustomExportService(jdbc, logs);
        long presidentId = requiredId(jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('20249999', '导出测试会长', 'test-hash', 'PRESIDENT', 'ACTIVE', 0)
                RETURNING id
                """, Long.class));

        AuthContext.set(new AuthUser(presidentId, "20249999", "导出测试会长", Role.PRESIDENT, Instant.now().plusSeconds(3600)));
        CustomExportService.ExportOptions presidentOptions = exports.options();
        assertFalse(presidentOptions.sources().stream().anyMatch(source -> "logs".equals(source.id())));
        assertThrows(ApiException.class, () -> exports.export(new CustomExportService.ExportRequest(
                "logs", List.of("createdAt"), Map.of(), "日志"
        )));

        AuthContext.set(new AuthUser(adminId, "admin", "管理员", Role.ADMIN, Instant.now().plusSeconds(3600)));
        CustomExportService.ExportOptions adminOptions = exports.options();
        assertEquals(6, adminOptions.sources().size());
        for (CustomExportService.SourceOption source : adminOptions.sources()) {
            List<String> fields = source.fields().stream()
                    .filter(CustomExportService.FieldOption::defaultSelected)
                    .map(CustomExportService.FieldOption::id)
                    .toList();
            Map<String, String> filters = new LinkedHashMap<>();
            source.filters().stream()
                    .filter(filter -> !filter.defaultValue().isBlank())
                    .forEach(filter -> filters.put(filter.id(), filter.defaultValue()));
            CustomExportService.ExportFile file = exports.export(new CustomExportService.ExportRequest(
                    source.id(), fields, filters, source.label()
            ));
            assertTrue(file.bytes().length > 1000);
            assertEquals("PK", new String(file.bytes(), 0, 2, java.nio.charset.StandardCharsets.US_ASCII));
        }

        CustomExportService.ExportFile members = exports.export(new CustomExportService.ExportRequest(
                "members",
                List.of("name", "studentNo"),
                Map.of("keyword", "测试成员"),
                "成员/自定义清单.xlsx"
        ));
        CustomExportService.ExportPreview membersPreview = exports.preview(new CustomExportService.ExportRequest(
                "members",
                List.of("name", "studentNo"),
                Map.of("keyword", "测试成员"),
                "预览不使用文件名"
        ));
        assertEquals("成员_自定义清单.xlsx", members.filename());
        assertEquals(1, members.rowCount());
        assertEquals(1, membersPreview.totalRows());
        assertEquals(List.of("name", "studentNo"), membersPreview.fields().stream()
                .map(CustomExportService.FieldOption::id)
                .toList());
        assertEquals(List.of("测试成员", "20240001"), List.copyOf(membersPreview.rows().getFirst().values()));
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(members.bytes()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("姓名", sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("学号", sheet.getRow(3).getCell(1).getStringCellValue());
            assertEquals("测试成员", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals("20240001", sheet.getRow(4).getCell(1).getStringCellValue());
        }
        assertThrows(ApiException.class, () -> exports.export(new CustomExportService.ExportRequest(
                "members", List.of("passwordHash"), Map.of(), "非法字段"
        )));
        assertTrue(jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = 'EXPORT_CUSTOM_DATA'",
                Integer.class
        ) >= 7);
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

    @Test
    void rejectsBackupEntryThatExpandsBeyondTheRestoreLimit() throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(compressed, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("metadata.json"));
            byte[] block = new byte[8192];
            int blocks = (16 * 1024 * 1024 / block.length) + 1;
            for (int index = 0; index < blocks; index++) {
                zip.write(block);
            }
            zip.closeEntry();
        }

        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "backup_oversized.zip",
                "application/zip",
                compressed.toByteArray()
        );
        ApiException error = assertThrows(ApiException.class, () -> backupService().restore(upload));

        assertTrue(error.getMessage().contains("解压后过大"));
    }

    @Test
    void legacyBackupWithoutAppSettingsKeepsCurrentSettings() throws Exception {
        BackupService backups = backupService();
        jdbc.update("""
                INSERT INTO app_settings (setting_key, setting_value, description)
                VALUES ('legacy_restore_test', 'before-backup', '兼容性测试')
                """);
        BackupService.BackupItem backup = backups.create();
        Path backupPath = new StoragePaths(tempDirectory.toString()).backupDirectory().resolve(backup.filename());

        jdbc.update("""
                UPDATE app_settings
                SET setting_value = 'keep-current'
                WHERE setting_key = 'legacy_restore_test'
                """);
        byte[] legacyBytes = withoutTables(Files.readAllBytes(backupPath), List.of("app_settings"));
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "backup_legacy.zip",
                "application/zip",
                legacyBytes
        );

        BackupService.RestoreResult result = backups.restore(upload);

        assertEquals("keep-current", jdbc.queryForObject("""
                SELECT setting_value
                FROM app_settings
                WHERE setting_key = 'legacy_restore_test'
                """, String.class));
        assertFalse(result.restoredRows().containsKey("app_settings"));
    }

    @Test
    void legacyBackupWithoutRepairSequenceRebuildsTheNextCaseNumber() throws Exception {
        RepairCaseService repairs = new RepairCaseService(
                jdbc,
                new OperationLogService(jdbc, objectMapper),
                backupService(),
                new UserRepository(jdbc)
        );
        createRepair(repairs, "旧备份编号一");
        createRepair(repairs, "旧备份编号二");
        createRepair(repairs, "旧备份编号三");

        BackupService backups = backupService();
        BackupService.BackupItem backup = backups.create();
        Path backupPath = new StoragePaths(tempDirectory.toString()).backupDirectory().resolve(backup.filename());
        byte[] legacyBytes = withoutTables(Files.readAllBytes(backupPath), List.of("repair_case_sequences"));
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "backup_without_repair_sequences.zip",
                "application/zip",
                legacyBytes
        );

        BackupService.RestoreResult result = backups.restore(upload);
        RepairCaseItem next = createRepair(repairs, "旧备份恢复后编号");

        assertFalse(result.restoredRows().containsKey("repair_case_sequences"));
        assertTrue(next.caseNo().endsWith("-0004"));
    }

    @Test
    void failedBackupDoesNotLeavePartialArchive() throws Exception {
        BackupService backups = backupService();
        jdbc.execute("DROP TABLE app_settings");

        assertThrows(RuntimeException.class, backups::create);

        Path backupDirectory = new StoragePaths(tempDirectory.toString()).backupDirectory();
        assertTrue(Files.isDirectory(backupDirectory));
        try (var files = Files.list(backupDirectory)) {
            assertEquals(0, files.count());
        }
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

    private RepairCaseItem createRepair(RepairCaseService repairs, String ownerName) {
        return repairs.create(new RepairCaseService.RepairCaseRequest(
                "PERSONAL_DEVICE",
                ownerName,
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
    }

    @SuppressWarnings("unchecked")
    private byte[] withoutTables(byte[] source, List<String> omittedTables) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(source), StandardCharsets.UTF_8);
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String tableName = entry.getName().replaceFirst("\\.json$", "");
                if (omittedTables.contains(tableName)) {
                    continue;
                }
                byte[] bytes = input.readAllBytes();
                if ("metadata.json".equals(entry.getName())) {
                    Map<String, Object> metadata = objectMapper.readValue(bytes, Map.class);
                    List<Object> tables = (List<Object>) metadata.get("tables");
                    metadata.put("tables", tables.stream()
                            .filter(table -> !omittedTables.contains(String.valueOf(table)))
                            .toList());
                    bytes = objectMapper.writeValueAsBytes(metadata);
                }
                zip.putNextEntry(new ZipEntry(entry.getName()));
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private MockMultipartFile scheduleImportFile(String filename, List<String[]> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("排班导入");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("星期");
            header.createCell(1).setCellValue("值班时段");
            header.createCell(2).setCellValue("学号");
            header.createCell(3).setCellValue("姓名");
            for (int index = 0; index < rows.size(); index++) {
                Row row = sheet.createRow(index + 1);
                String[] values = rows.get(index);
                for (int column = 0; column < values.length; column++) {
                    row.createCell(column).setCellValue(values[column]);
                }
            }
            workbook.write(output);
            return new MockMultipartFile("file", filename, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
