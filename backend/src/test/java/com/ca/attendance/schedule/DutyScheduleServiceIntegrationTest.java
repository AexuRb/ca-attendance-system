package com.ca.attendance.schedule;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.config.DatabaseMigrator;
import com.ca.attendance.config.SQLiteDataSourceConfiguration;
import com.ca.attendance.config.StoragePaths;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.settings.DutyPeriodService;
import com.ca.attendance.settings.DutyWeekdayService;
import tools.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DutyScheduleServiceIntegrationTest {
    @TempDir
    Path tempDirectory;

    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private DutyScheduleService schedules;
    private DutyPeriodService periods;
    private DutyWeekdayService weekdays;
    private DutyScheduleImportService imports;
    private long ministerId;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = (HikariDataSource) new SQLiteDataSourceConfiguration()
                .dataSource(new StoragePaths(tempDirectory.toString()));
        new DatabaseMigrator(dataSource).run();
        jdbc = new JdbcTemplate(dataSource);
        ObjectMapper objectMapper = new ObjectMapper();
        OperationLogService logs = new OperationLogService(jdbc, objectMapper);
        long adminId = insertUser("1000", "管理员", "ADMIN", "ACTIVE");
        ministerId = insertUser("1001", "张部长", "MINISTER", "ACTIVE");
        insertUser("1002", "停用部长", "MINISTER", "DISABLED");
        insertUser("1003", "普通成员", "MEMBER", "ACTIVE");
        AuthContext.set(new AuthUser(adminId, "1000", "管理员", Role.ADMIN, Instant.now().plusSeconds(3600)));

        periods = new DutyPeriodService(jdbc, objectMapper, logs);
        periods.update(List.of(new DutyPeriodService.DutyPeriodRequest("14:00", "16:00")));
        weekdays = new DutyWeekdayService(jdbc, logs);
        schedules = new DutyScheduleService(jdbc, logs, periods);
        imports = new DutyScheduleImportService(jdbc, logs, periods);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void assigneeCandidatesOnlyContainActiveManagersAndSupportSearch() {
        var candidates = schedules.assigneeCandidates("张");

        assertEquals(1, candidates.size());
        assertEquals("1001", candidates.getFirst().studentNo());
        assertEquals(Role.MINISTER, candidates.getFirst().role());
    }

    @Test
    void ministerCannotListManagementSchedules() {
        AuthContext.set(new AuthUser(
                ministerId,
                "1001",
                "张部长",
                Role.MINISTER,
                Instant.now().plusSeconds(3600)
        ));

        ApiException denied = assertThrows(ApiException.class, schedules::list);

        assertEquals(403, denied.status().value());
        assertTrue(denied.getMessage().contains("会长或管理员"));
    }

    @Test
    void createRejectsOrdinaryOrDisabledMembersAsAssignees() {
        ApiException ordinary = assertThrows(ApiException.class, () -> schedules.create(request("1003")));
        ApiException disabled = assertThrows(ApiException.class, () -> schedules.create(request("1002")));

        assertTrue(ordinary.getMessage().contains("启用中的部长、会长或管理员"));
        assertTrue(disabled.getMessage().contains("启用中的部长、会长或管理员"));
    }

    @Test
    void dutyPeriodsPreserveOrderAndCannotDisableAReferencedPeriod() {
        var reordered = periods.update(List.of(
                new DutyPeriodService.DutyPeriodRequest("16:00", "18:00", true),
                new DutyPeriodService.DutyPeriodRequest("14:00", "16:00", true)
        ));
        schedules.create(request("1001"));

        assertEquals("16:00", reordered.getFirst().startTime());
        assertEquals(0, reordered.getFirst().sortOrder());
        ApiException conflict = assertThrows(ApiException.class, () -> periods.update(List.of(
                new DutyPeriodService.DutyPeriodRequest("16:00", "18:00", true),
                new DutyPeriodService.DutyPeriodRequest("14:00", "16:00", false)
        )));
        assertTrue(conflict.getMessage().contains("固定排班"));
    }

    @Test
    void dutyPeriodsRejectOverlappingEnabledRanges() {
        ApiException overlap = assertThrows(ApiException.class, () -> periods.update(List.of(
                new DutyPeriodService.DutyPeriodRequest("14:00", "16:00", true),
                new DutyPeriodService.DutyPeriodRequest("15:00", "17:00", true)
        )));

        assertTrue(overlap.getMessage().contains("不能重叠"));
    }

    @Test
    void createRejectsAWeekdayThatIsNotEnabledInSettings() {
        ApiException disabledWeekday = assertThrows(
                ApiException.class,
                () -> schedules.create(request(7, "1001"))
        );

        assertTrue(disabledWeekday.getMessage().contains("未启用"));
    }

    @Test
    void weekdaySettingsCannotDisableAReferencedFixedSchedule() {
        schedules.create(request("1001"));

        ApiException conflict = assertThrows(
                ApiException.class,
                () -> weekdays.update(List.of(2, 3, 4, 5))
        );

        assertTrue(conflict.getMessage().contains("固定排班"));
        assertTrue(weekdays.isDutyWeekday(1));
    }

    @Test
    void publicScheduleQueriesHideLegacySlotsOnDisabledWeekdays() {
        LocalDate monday = LocalDate.of(2026, 8, 10);
        schedules.create(request("1001"));
        jdbc.update("UPDATE duty_weekday_settings SET enabled = 0 WHERE weekday = 1");

        assertTrue(schedules.today(monday).isEmpty());
        assertTrue(schedules.week(monday).isEmpty());
        assertEquals(1, schedules.list().size());
    }

    @Test
    void generatedTemplatePreservesTextIdsThroughLastAllowedRowAndReplacesOnlyFilledGroup() throws Exception {
        weekdays.update(List.of(1, 2));
        String leadingZero = "000001";
        String longNumber = "00123456789012345678901234567890";
        insertUser(leadingZero, "文本部长甲", "MINISTER", "ACTIVE");
        insertUser(longNumber, "文本部长乙", "MINISTER", "ACTIVE");
        var monday = schedules.create(request(1, "1001"));
        var tuesday = schedules.create(request(2, "1001"));
        Path template = tempDirectory.resolve("schedule-template.xlsx");
        Files.write(template, imports.exportTemplate().bytes());

        byte[] filled;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(Files.readAllBytes(template)))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("排班导入", sheet.getSheetName());
            assertEquals(4, sheet.getRow(0).getLastCellNum());
            assertEquals(List.of("星期", "值班时段", "学号", "姓名"),
                    java.util.stream.IntStream.range(0, 4)
                            .mapToObj(i -> sheet.getRow(0).getCell(i).getStringCellValue()).toList());
            assertEquals(2, sheet.getLastRowNum());
            assertEquals("星期一", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("星期二", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("14:00-16:00", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("@", sheet.getColumnStyle(2).getDataFormatString());
            assertEquals("@", sheet.getRow(1).getCell(2).getCellStyle().getDataFormatString());
            assertEquals("", sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals("", sheet.getRow(1).getCell(3).getStringCellValue());
            assertEquals("", sheet.getRow(2).getCell(2).getStringCellValue());
            assertEquals("", sheet.getRow(2).getCell(3).getStringCellValue());
            assertTrue(sheet.getPaneInformation().isFreezePane());
            assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition());
            sheet.getRow(1).getCell(2).setCellValue(leadingZero);
            Row last = sheet.createRow(1000);
            last.createCell(0).setCellValue("星期一");
            last.createCell(1).setCellValue("14:00-16:00");
            last.createCell(2).setCellValue(longNumber);
            assertEquals("@", last.getCell(2).getCellStyle().getDataFormatString());
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                workbook.write(output);
                filled = output.toByteArray();
            }
        }
        Path filledFile = tempDirectory.resolve("schedule-filled.xlsx");
        Files.write(filledFile, filled);
        try (Workbook reopened = WorkbookFactory.create(filledFile.toFile(), null, true)) {
            assertEquals(leadingZero, reopened.getSheetAt(0).getRow(1).getCell(2).getStringCellValue());
            assertEquals(longNumber, reopened.getSheetAt(0).getRow(1000).getCell(2).getStringCellValue());
        }
        var preview = imports.preview(scheduleFile(filled));
        assertTrue(preview.valid());
        assertEquals(2, preview.sourceRows());
        assertEquals(1, preview.groupCount());
        imports.importSchedules(scheduleFile(filled));
        assertEquals(List.of(leadingZero, longNumber), jdbc.queryForList(
                "SELECT student_no_snapshot FROM duty_schedule_assignees WHERE slot_id = ? ORDER BY sort_order",
                String.class, monday.id()));
        assertEquals(List.of("1001"), jdbc.queryForList(
                "SELECT student_no_snapshot FROM duty_schedule_assignees WHERE slot_id = ?",
                String.class, tuesday.id()));
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(Files.readAllBytes(filledFile)));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.getSheetAt(0).createRow(1001).createCell(2).setCellValue("");
            workbook.write(output);
            assertTrue(imports.preview(scheduleFile(output.toByteArray())).issues().stream()
                    .anyMatch(issue -> issue.message().contains("1000")));
        }
    }

    private MockMultipartFile scheduleFile(byte[] bytes) {
        return new MockMultipartFile("file", "schedule.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private DutyScheduleService.SlotRequest request(String studentNo) {
        return request(1, studentNo);
    }

    private DutyScheduleService.SlotRequest request(int weekday, String studentNo) {
        return new DutyScheduleService.SlotRequest(
                weekday,
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                "部长值班",
                "协会办公室",
                null,
                true,
                List.of(new DutyScheduleService.AssigneeRequest(studentNo, null))
        );
    }

    private long insertUser(String studentNo, String name, String role, String status) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (
                  student_no, name, password_hash, role, status, must_change_password
                )
                VALUES (?, ?, 'test-hash', ?, ?, 0)
                RETURNING id
                """, Long.class, studentNo, name, role, status);
        return id == null ? 0 : id;
    }
}
