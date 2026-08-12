package com.ca.attendance.export;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.config.DatabaseMigrator;
import com.ca.attendance.config.SQLiteDataSourceConfiguration;
import com.ca.attendance.config.StoragePaths;
import com.ca.attendance.log.OperationLogQueryService;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.maintenance.BackupService;
import com.ca.attendance.repair.RepairCaseService;
import com.ca.attendance.schedule.DutyScheduleImportService;
import com.ca.attendance.settings.DutyPeriodService;
import com.ca.attendance.stats.StatsService;
import com.ca.attendance.training.TrainingService;
import com.ca.attendance.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportAuditIntegrationTest {
    @TempDir
    Path tempDirectory;

    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private ObjectMapper objectMapper;
    private StatsService stats;
    private TrainingService trainings;
    private RepairCaseService repairs;
    private OperationLogQueryService operationLogs;
    private CustomExportService customExports;
    private DutyScheduleImportService scheduleImports;
    private long adminId;
    private long memberId;
    private long sessionId;
    private LocalDate dataDate;

    @BeforeEach
    void setUp() throws Exception {
        StoragePaths storagePaths = new StoragePaths(tempDirectory.toString());
        dataSource = (HikariDataSource) new SQLiteDataSourceConfiguration().dataSource(storagePaths);
        new DatabaseMigrator(dataSource).run();
        jdbc = new JdbcTemplate(dataSource);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        adminId = insertUser("admin", "审计管理员", "ADMIN");
        memberId = insertUser("20260001", "导出测试成员", "MEMBER");
        useUser(adminId, "admin", "审计管理员", Role.ADMIN);

        OperationLogService audit = new OperationLogService(jdbc, objectMapper);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        BackupService backups = new BackupService(
                jdbc,
                objectMapper,
                transactions,
                new TokenService(12),
                storagePaths
        );
        stats = new StatsService(jdbc, audit);
        trainings = new TrainingService(jdbc, audit);
        repairs = new RepairCaseService(jdbc, audit, backups, new UserRepository(jdbc));
        operationLogs = new OperationLogQueryService(jdbc, backups, audit);
        customExports = new CustomExportService(jdbc, audit);
        DutyPeriodService dutyPeriods = new DutyPeriodService(jdbc, objectMapper, audit);
        dutyPeriods.update(List.of(new DutyPeriodService.DutyPeriodRequest("14:00", "16:00")));
        scheduleImports = new DutyScheduleImportService(jdbc, audit, dutyPeriods);
        jdbc.update("DELETE FROM operation_logs");

        dataDate = LocalDate.of(2026, 8, 10);
        insertBusinessData();
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void successfulBusinessExportsWriteUnifiedAuditMatchingWorkbookRows() throws Exception {
        StatsService.ExportFile statsFile = stats.export(dataDate, dataDate);
        byte[] statsBytes = statsFile.bytes();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(statsBytes))) {
            assertEquals(1, workbook.getSheet("值班记录").getLastRowNum() - 1);
        }
        assertLatestAudit("ATTENDANCE_STATS", 1, "值班记录_2026-08-10_2026-08-10.xlsx");

        TrainingService.ExportFile sessionFile = trainings.exportSession(sessionId);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(sessionFile.bytes()))) {
            assertEquals(1, workbook.getSheet("培训名单").getLastRowNum() - 3);
        }
        assertLatestAudit("TRAINING_SESSION", 1, sessionFile.filename());

        TrainingService.ExportFile summaryFile = trainings.exportSummary(null, null, dataDate, dataDate);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(summaryFile.bytes()))) {
            assertEquals(1, workbook.getSheet("培训场次").getLastRowNum() - 2);
            assertEquals(1, workbook.getSheet("成员统计").getLastRowNum());
        }
        JsonNode summaryAudit = assertLatestAudit("TRAINING_SUMMARY", 2, summaryFile.filename());
        assertEquals(1, summaryAudit.path("details").path("sessionRows").asInt());
        assertEquals(1, summaryAudit.path("details").path("memberRows").asInt());

        RepairCaseService.ExportFile repairFile = repairs.exportCases(null, "ALL", dataDate, dataDate);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(repairFile.bytes()))) {
            assertEquals(1, workbook.getSheet("维修事务").getLastRowNum() - 2);
        }
        assertLatestAudit("REPAIR_CASES", 1, repairFile.filename());

        CustomExportService.ExportFile customFile = customExports.export(new CustomExportService.ExportRequest(
                "members",
                List.of("studentNo", "name"),
                Map.of("keyword", "20260001"),
                "审计成员清单"
        ));
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(customFile.bytes()))) {
            assertEquals(1, customFile.rowCount());
            assertEquals("导出测试成员", workbook.getSheetAt(0).getRow(4).getCell(1).getStringCellValue());
        }
        assertLatestAudit("CUSTOM_MEMBERS", 1, customFile.filename());

        int snapshotRows = operationLogCount();
        OperationLogQueryService.ExportFile logFile = operationLogs.export(null, null, null, null);
        byte[] logBytes = logFile.bytes();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(logBytes))) {
            assertEquals(snapshotRows, workbook.getSheet("操作日志").getLastRowNum());
        }
        assertEquals(snapshotRows + 1, operationLogCount());
        assertLatestAudit("OPERATION_LOGS", snapshotRows, "操作日志.xlsx");
    }

    @Test
    void failedBusinessExportsDoNotWriteSuccessAudit() {
        assertThrows(ApiException.class, () -> stats.export(dataDate.plusDays(1), dataDate));
        assertThrows(ApiException.class, () -> trainings.exportSession(999_999));
        assertThrows(ApiException.class, () -> trainings.exportSummary(null, null, dataDate.plusDays(1), dataDate));
        assertThrows(ApiException.class, () -> repairs.exportCases(null, "ALL", dataDate.plusDays(1), dataDate));
        assertThrows(ApiException.class, () -> operationLogs.export(null, null, "not-a-date", null));
        assertThrows(ApiException.class, () -> customExports.export(new CustomExportService.ExportRequest(
                "members", List.of("passwordHash"), Map.of(), "非法导出"
        )));

        assertEquals(0, operationLogCount());
    }

    @Test
    void deniedBusinessExportsDoNotWriteSuccessAudit() {
        useUser(memberId, "20260001", "导出测试成员", Role.MEMBER);

        assertThrows(ApiException.class, () -> stats.export(dataDate, dataDate));
        assertThrows(ApiException.class, () -> trainings.exportSession(sessionId));
        assertThrows(ApiException.class, () -> trainings.exportSummary(null, null, dataDate, dataDate));
        assertThrows(ApiException.class, () -> repairs.exportCases(null, "ALL", dataDate, dataDate));
        assertThrows(ApiException.class, () -> operationLogs.export(null, null, null, null));
        assertThrows(ApiException.class, () -> customExports.export(new CustomExportService.ExportRequest(
                "members", List.of("studentNo"), Map.of(), "越权导出"
        )));

        assertEquals(0, operationLogCount());
    }

    @Test
    void templatesAndAgreementPreviewDoNotCreateBusinessExportAudit() {
        TrainingService.ExportFile general = trainings.exportImportTemplate();
        TrainingService.ExportFile session = trainings.exportSessionImportTemplate(sessionId);
        DutyScheduleImportService.ExportFile schedule = scheduleImports.exportTemplate();
        RepairCaseService.AgreementFile agreement = repairs.agreement(repairId());

        assertTrue(general.bytes().length > 1000);
        assertTrue(session.bytes().length > 1000);
        assertTrue(schedule.bytes().length > 1000);
        assertTrue(agreement.bytes().length > 1000);
        assertEquals(0, operationLogCount());
    }

    private JsonNode assertLatestAudit(String exportType, int rows, String filename) throws Exception {
        Map<String, Object> record = jdbc.queryForMap("""
                SELECT action_type AS actionType, target_type AS targetType,
                       operator_student_no AS operatorStudentNo, after_data AS afterData
                FROM operation_logs
                ORDER BY id DESC
                LIMIT 1
                """);
        assertEquals("EXPORT_DATA", record.get("actionType"));
        assertEquals("data_exports", record.get("targetType"));
        assertEquals("admin", record.get("operatorStudentNo"));
        JsonNode payload = objectMapper.readTree(String.valueOf(record.get("afterData")));
        assertEquals(exportType, payload.path("exportType").asText());
        assertEquals("ADMIN", payload.path("operatorRole").asText());
        assertEquals(rows, payload.path("rows").asInt());
        assertEquals(filename, payload.path("filename").asText());
        assertTrue(payload.path("filters").isObject());
        return payload;
    }

    private void insertBusinessData() {
        jdbc.update("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                  check_in_time, check_out_time, check_in_status, check_out_status,
                  duration_minutes, valid_hours, effective_status
                )
                VALUES (?, '20260001', '导出测试成员', ?, 1, ?, ?,
                        'APPROVED', 'APPROVED', 120, 2, 'VALID')
                """, memberId, dataDate, dataDate.atTime(14, 0), dataDate.atTime(16, 0));
        sessionId = requiredId(jdbc.queryForObject("""
                INSERT INTO training_sessions (
                  title, training_date, start_time, end_time, location, speaker, status, created_by, updated_by
                )
                VALUES ('导出审计培训', ?, '16:00:00', '17:30:00', '活动室', '主讲人', 'COMPLETED', ?, ?)
                RETURNING id
                """, Long.class, dataDate, adminId, adminId));
        jdbc.update("""
                INSERT INTO training_participants (
                  session_id, user_id, student_no_snapshot, name_snapshot,
                  duration_hours, created_by, updated_by
                )
                VALUES (?, ?, '20260001', '导出测试成员', ?, ?, ?)
                """, sessionId, memberId, new BigDecimal("1.50"), adminId, adminId);
        jdbc.update("""
                INSERT INTO repair_cases (
                  case_no, agreement_type, owner_name, device_type, fault_description,
                  status, received_at, created_by, updated_by
                )
                VALUES ('JXWX20260810-0001', 'PERSONAL_DEVICE', '送修同学', '笔记本电脑',
                        '无法开机', 'REPAIRING', ?, ?, ?)
                """, Timestamp.valueOf(dataDate.atTime(10, 0)), adminId, adminId);
    }

    private long insertUser(String studentNo, String name, String role) {
        return requiredId(jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES (?, ?, 'test-hash', ?, 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name, role));
    }

    private void useUser(long id, String studentNo, String name, Role role) {
        AuthContext.set(new AuthUser(id, studentNo, name, role, Instant.now().plusSeconds(3600)));
    }

    private int operationLogCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM operation_logs", Integer.class);
        return count == null ? 0 : count;
    }

    private long repairId() {
        Long id = jdbc.queryForObject("SELECT id FROM repair_cases WHERE case_no = 'JXWX20260810-0001'", Long.class);
        return requiredId(id);
    }

    private long requiredId(Long value) {
        assertNotNull(value);
        return value;
    }
}
