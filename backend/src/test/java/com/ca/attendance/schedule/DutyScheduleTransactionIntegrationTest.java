package com.ca.attendance.schedule;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.Role;
import com.ca.attendance.settings.DutyPeriodService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DutyScheduleTransactionIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();

    @Autowired
    private DutyScheduleService schedules;

    @Autowired
    private DutyScheduleImportService imports;

    @Autowired
    private DutyPeriodService periods;

    @Autowired
    private JdbcTemplate jdbc;

    private long adminId;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    @BeforeEach
    void setUp() {
        dropAuditTrigger();
        jdbc.update("DELETE FROM operation_logs");
        jdbc.update("DELETE FROM duty_schedule_assignees");
        jdbc.update("DELETE FROM duty_schedule_slots");

        adminId = ensureUser("9910000000", "排班事务管理员", "ADMIN");
        ensureUser("9910000001", "排班部长甲", "MINISTER");
        ensureUser("9910000002", "排班部长乙", "MINISTER");
        AuthContext.set(new AuthUser(
                adminId,
                "9910000000",
                "排班事务管理员",
                Role.ADMIN,
                Instant.now().plusSeconds(3600)
        ));
        periods.update(List.of(new DutyPeriodService.DutyPeriodRequest("14:00", "16:00")));
        jdbc.update("DELETE FROM operation_logs");
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        dropAuditTrigger();
    }

    @Test
    void createRollsBackWhenAuditLogFails() {
        failAudit("CREATE_DUTY_SCHEDULE");

        assertThrows(DataAccessException.class, () -> schedules.create(request("排班新增", "9910000001")));

        assertEquals(0, activeSlotCount());
    }

    @Test
    void updateRollsBackWhenAuditLogFails() {
        DutyScheduleSlotItem slot = schedules.create(request("排班修改前", "9910000001"));
        failAudit("UPDATE_DUTY_SCHEDULE");

        assertThrows(DataAccessException.class, () -> schedules.update(
                slot.id(),
                request("排班修改后", "9910000002")
        ));

        assertEquals("排班修改前", slotTitle(slot.id()));
        assertEquals(List.of("9910000001"), assigneeStudentNos(slot.id()));
    }

    @Test
    void archiveRollsBackWhenAuditLogFails() {
        DutyScheduleSlotItem slot = schedules.create(request("排班归档", "9910000001"));
        failAudit("ARCHIVE_DUTY_SCHEDULE");

        assertThrows(DataAccessException.class, () -> schedules.archive(slot.id()));

        assertEquals("ACTIVE", slotStatus(slot.id()));
    }

    @Test
    void importRollsBackReplacementWhenAuditLogFails() throws Exception {
        DutyScheduleSlotItem slot = schedules.create(request("排班导入", "9910000001"));
        failAudit("IMPORT_DUTY_SCHEDULES");

        assertThrows(DataAccessException.class, () -> imports.importSchedules(importFile("9910000002", "排班部长乙")));

        assertEquals(1, activeSlotCount());
        assertEquals(List.of("9910000001"), assigneeStudentNos(slot.id()));
        assertEquals(0, actionCount("IMPORT_DUTY_SCHEDULES"));
    }

    private DutyScheduleService.SlotRequest request(String title, String studentNo) {
        return new DutyScheduleService.SlotRequest(
                1,
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                title,
                "协会办公室",
                null,
                true,
                List.of(new DutyScheduleService.AssigneeRequest(studentNo, null))
        );
    }

    private MockMultipartFile importFile(String studentNo, String name) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("固定周表");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("星期");
            header.createCell(1).setCellValue("时段");
            header.createCell(2).setCellValue("学号");
            header.createCell(3).setCellValue("姓名");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("星期一");
            row.createCell(1).setCellValue("14:00-16:00");
            row.createCell(2).setCellValue(studentNo);
            row.createCell(3).setCellValue(name);
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "schedule.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private long ensureUser(String studentNo, String name, String role) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM users WHERE student_no = ?", Long.class, studentNo);
        if (!ids.isEmpty()) {
            jdbc.update("UPDATE users SET name = ?, role = ?, status = 'ACTIVE' WHERE id = ?", name, role, ids.getFirst());
            return ids.getFirst();
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES (?, ?, 'test-hash', ?, 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name, role);
        if (id == null) {
            throw new IllegalStateException("排班事务测试账号创建失败");
        }
        return id;
    }

    private void failAudit(String actionType) {
        dropAuditTrigger();
        jdbc.execute("""
                CREATE TRIGGER fail_schedule_audit
                BEFORE INSERT ON operation_logs
                WHEN NEW.action_type = '%s'
                BEGIN
                  SELECT RAISE(ABORT, 'forced audit failure');
                END
                """.formatted(actionType));
    }

    private void dropAuditTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_schedule_audit");
    }

    private int activeSlotCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM duty_schedule_slots WHERE status = 'ACTIVE'",
                Integer.class
        );
    }

    private String slotTitle(long id) {
        return jdbc.queryForObject("SELECT title FROM duty_schedule_slots WHERE id = ?", String.class, id);
    }

    private String slotStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM duty_schedule_slots WHERE id = ?", String.class, id);
    }

    private List<String> assigneeStudentNos(long slotId) {
        return jdbc.queryForList(
                "SELECT student_no_snapshot FROM duty_schedule_assignees WHERE slot_id = ? ORDER BY sort_order, id",
                String.class,
                slotId
        );
    }

    private int actionCount(String actionType) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = ?",
                Integer.class,
                actionType
        );
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("ca-schedule-transaction-test-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
