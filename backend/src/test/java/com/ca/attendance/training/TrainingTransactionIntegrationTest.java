package com.ca.attendance.training;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TrainingTransactionIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();

    @Autowired
    private TrainingService trainings;

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
        jdbc.update("""
                DELETE FROM training_participants
                WHERE session_id IN (
                  SELECT id FROM training_sessions WHERE title LIKE '事务培训%'
                )
                """);
        jdbc.update("DELETE FROM training_sessions WHERE title LIKE '事务培训%'");

        List<Long> existingAdminIds = jdbc.queryForList(
                "SELECT id FROM users WHERE student_no = 'tx-training-admin'",
                Long.class
        );
        adminId = existingAdminIds.isEmpty()
                ? requiredId(jdbc.queryForObject("""
                    INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                    VALUES ('tx-training-admin', '培训事务管理员', 'test-hash', 'ADMIN', 'ACTIVE', 0)
                    RETURNING id
                    """, Long.class))
                : existingAdminIds.getFirst();
        AuthContext.set(new AuthUser(
                adminId,
                "tx-training-admin",
                "培训事务管理员",
                Role.ADMIN,
                Instant.now().plusSeconds(3600)
        ));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        dropAuditTrigger();
    }

    @Test
    void createRollsBackWhenAuditLogFails() {
        failAudit("CREATE_TRAINING");

        assertThrows(DataAccessException.class, () -> trainings.create(sessionRequest("事务培训新增")));

        assertEquals(0, sessionCount("事务培训新增"));
    }

    @Test
    void createRejectsTitleLongerThanOneHundredCharacters() {
        String title = "事务培训" + "长".repeat(101);

        ApiException exception = assertThrows(ApiException.class, () -> trainings.create(sessionRequest(title)));

        assertTrue(exception.getMessage().contains("培训标题不能超过 100 个字符"));
        assertEquals(0, sessionCount(title));
    }

    @Test
    void updateRollsBackWhenAuditLogFails() {
        TrainingSessionItem session = trainings.create(sessionRequest("事务培训修改前"));
        failAudit("UPDATE_TRAINING");

        assertThrows(DataAccessException.class, () -> trainings.update(
                session.id(),
                sessionRequest("事务培训修改后")
        ));

        assertEquals("事务培训修改前", sessionTitle(session.id()));
    }

    @Test
    void archiveRollsBackWhenAuditLogFails() {
        TrainingSessionItem session = trainings.create(sessionRequest("事务培训归档"));
        failAudit("ARCHIVE_TRAINING");

        assertThrows(DataAccessException.class, () -> trainings.archive(session.id()));

        assertEquals("PLANNED", sessionStatus(session.id()));
    }

    @Test
    void archiveRejectsAnIgnoredDatabaseWriteWithoutLoggingSuccess() {
        TrainingSessionItem session = trainings.create(sessionRequest("事务培训零行归档"));
        jdbc.execute("""
                CREATE TRIGGER ignore_training_archive
                BEFORE UPDATE ON training_sessions
                WHEN OLD.id = %d AND NEW.status = 'ARCHIVED'
                BEGIN
                  SELECT RAISE(IGNORE);
                END
                """.formatted(session.id()));

        assertThrows(ApiException.class, () -> trainings.archive(session.id()));

        assertEquals("PLANNED", sessionStatus(session.id()));
        assertEquals(0, actionCount("ARCHIVE_TRAINING"));
    }

    @Test
    void participantCreateRollsBackWhenAuditLogFails() {
        TrainingSessionItem session = trainings.create(sessionRequest("事务培训参与新增"));
        failAudit("CREATE_TRAINING_PARTICIPANT");

        assertThrows(DataAccessException.class, () -> trainings.addParticipant(
                session.id(),
                participantRequest("9900000101", "参与新增成员")
        ));

        assertEquals(0, participantCount(session.id()));
    }

    @Test
    void participantUpdateRollsBackWhenAuditLogFails() {
        TrainingSessionItem session = trainings.create(sessionRequest("事务培训参与修改"));
        TrainingParticipantItem participant = trainings.addParticipant(
                session.id(),
                participantRequest("9900000102", "参与修改前")
        );
        failAudit("UPDATE_TRAINING_PARTICIPANT");

        assertThrows(DataAccessException.class, () -> trainings.updateParticipant(
                session.id(),
                participant.id(),
                participantRequest("9900000102", "参与修改后")
        ));

        assertEquals("参与修改前", participantName(participant.id()));
    }

    @Test
    void participantDeleteRollsBackWhenAuditLogFails() {
        TrainingSessionItem session = trainings.create(sessionRequest("事务培训参与删除"));
        TrainingParticipantItem participant = trainings.addParticipant(
                session.id(),
                participantRequest("9900000103", "参与删除成员")
        );
        failAudit("DELETE_TRAINING_PARTICIPANT");

        assertThrows(DataAccessException.class, () -> trainings.deleteParticipant(session.id(), participant.id()));

        assertEquals(1, participantCount(session.id()));
    }

    @Test
    void participantImportRollsBackWhenAuditLogFails() throws Exception {
        TrainingSessionItem session = trainings.create(sessionRequest("事务培训名单导入"));
        failAudit("IMPORT_TRAINING_PARTICIPANTS");

        assertThrows(DataAccessException.class, () -> trainings.importParticipants(
                session.id(),
                participantImportFile("9900000104", "名单导入成员")
        ));

        assertEquals(0, participantCount(session.id()));
    }

    @Test
    void participantImportRejectsWholeWorkbookWhenAnyRowIsInvalid() throws Exception {
        TrainingSessionItem session = trainings.create(sessionRequest("事务培训原子导入"));

        ApiException exception = assertThrows(ApiException.class, () -> trainings.importParticipants(
                session.id(),
                participantImportFileWithInvalidTrailingRow()
        ));

        assertTrue(exception.getMessage().contains("未写入"));
        assertEquals(0, participantCount(session.id()));
        assertEquals(0, actionCount("IMPORT_TRAINING_PARTICIPANTS"));
    }

    @Test
    void participantImportRejectsWholeWorkbookWhenMemberMatchingFails() throws Exception {
        TrainingSessionItem session = trainings.create(sessionRequest("事务培训匹配失败"));

        ApiException exception = assertThrows(ApiException.class, () -> trainings.importParticipants(
                session.id(),
                participantImportFileWithUnmatchedName()
        ));

        assertTrue(exception.getMessage().contains("姓名未能唯一匹配成员"));
        assertTrue(exception.getMessage().contains("未写入"));
        assertEquals(0, participantCount(session.id()));
        assertEquals(0, actionCount("IMPORT_TRAINING_PARTICIPANTS"));
    }

    @Test
    void participantImportReportsCreatedAndUpdatedRowsAfterAtomicWrite() throws Exception {
        TrainingSessionItem session = trainings.create(sessionRequest("事务培训名单计数"));
        trainings.addParticipant(
                session.id(),
                participantRequest("9900000108", "导入前姓名")
        );

        TrainingService.ImportResult result = trainings.importParticipants(
                session.id(),
                participantImportFileForCreateAndUpdate()
        );

        assertEquals(1, result.created());
        assertEquals(1, result.updated());
        assertEquals(0, result.skipped());
        assertTrue(result.errors().isEmpty());
        assertEquals(2, participantCount(session.id()));
        assertEquals("导入后姓名", participantNameByStudent(session.id(), "9900000108"));
        assertEquals(0, new BigDecimal("1.50").compareTo(
                participantDurationByStudent(session.id(), "9900000108")
        ));
        assertEquals(1, actionCount("IMPORT_TRAINING_PARTICIPANTS"));
    }

    @Test
    void participantImportRejectsFilesLargerThanFiveMegabytesBeforeParsing() {
        TrainingSessionItem session = trainings.create(sessionRequest("培训名单体积限制"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "participants.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[5 * 1024 * 1024 + 1]
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> trainings.importParticipants(session.id(), file)
        );

        assertTrue(exception.getMessage().contains("不能超过 5 MB"));
        assertEquals(0, participantCount(session.id()));
        assertEquals(0, actionCount("IMPORT_TRAINING_PARTICIPANTS"));
    }

    @Test
    void sessionTemplatePrefillsSpeakerAsFirstParticipant() throws Exception {
        TrainingSessionItem session = trainings.create(sessionRequest("事务培训主讲人模板"));
        TrainingService.ExportFile template = trainings.exportSessionImportTemplate(session.id());

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template.bytes()))) {
            Row speaker = workbook.getSheetAt(0).getRow(1);
            assertEquals("事务测试主讲人", speaker.getCell(1).getStringCellValue());
            assertEquals(2.0, speaker.getCell(2).getNumericCellValue());
            assertEquals("主讲人", speaker.getCell(3).getStringCellValue());
        }
    }

    private TrainingService.SessionRequest sessionRequest(String title) {
        return new TrainingService.SessionRequest(
                title,
                LocalDate.now(),
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                "事务测试地点",
                "事务测试主讲人",
                null,
                "PLANNED"
        );
    }

    private TrainingService.ParticipantRequest participantRequest(String studentNo, String name) {
        return new TrainingService.ParticipantRequest(
                studentNo,
                name,
                new BigDecimal("2.0"),
                null
        );
    }

    private MockMultipartFile participantImportFile(String studentNo, String name) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("参与名单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("时长");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(studentNo);
            row.createCell(1).setCellValue(name);
            row.createCell(2).setCellValue(2);
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "participants.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private MockMultipartFile participantImportFileWithInvalidTrailingRow() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("参与名单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("时长");
            Row valid = sheet.createRow(1);
            valid.createCell(0).setCellValue("9900000105");
            valid.createCell(1).setCellValue("原子导入成员");
            valid.createCell(2).setCellValue(2);
            Row invalid = sheet.createRow(2);
            invalid.createCell(0).setCellValue("9900000106");
            invalid.createCell(2).setCellValue(2);
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "participants-invalid.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private MockMultipartFile participantImportFileWithUnmatchedName() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("参与名单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("时长");
            Row valid = sheet.createRow(1);
            valid.createCell(0).setCellValue("9900000107");
            valid.createCell(1).setCellValue("合法名单成员");
            valid.createCell(2).setCellValue(2);
            Row unmatched = sheet.createRow(2);
            unmatched.createCell(1).setCellValue("无法匹配的成员姓名");
            unmatched.createCell(2).setCellValue(2);
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "participants-unmatched.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private MockMultipartFile participantImportFileForCreateAndUpdate() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("参与名单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("时长");
            Row update = sheet.createRow(1);
            update.createCell(0).setCellValue("9900000108");
            update.createCell(1).setCellValue("导入后姓名");
            update.createCell(2).setCellValue(1.5);
            Row create = sheet.createRow(2);
            create.createCell(0).setCellValue("9900000109");
            create.createCell(1).setCellValue("新增名单成员");
            create.createCell(2).setCellValue(2);
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "participants-create-update.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private void failAudit(String actionType) {
        dropAuditTrigger();
        jdbc.execute("""
                CREATE TRIGGER fail_training_audit
                BEFORE INSERT ON operation_logs
                WHEN NEW.action_type = '%s'
                BEGIN
                  SELECT RAISE(ABORT, 'forced audit failure');
                END
                """.formatted(actionType));
    }

    private void dropAuditTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_training_audit");
        jdbc.execute("DROP TRIGGER IF EXISTS ignore_training_archive");
    }

    private int sessionCount(String title) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_sessions WHERE title = ?",
                Integer.class,
                title
        );
    }

    private String sessionTitle(long id) {
        return jdbc.queryForObject("SELECT title FROM training_sessions WHERE id = ?", String.class, id);
    }

    private String sessionStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM training_sessions WHERE id = ?", String.class, id);
    }

    private int participantCount(long sessionId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_participants WHERE session_id = ?",
                Integer.class,
                sessionId
        );
    }

    private int actionCount(String actionType) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = ?",
                Integer.class,
                actionType
        );
        return count == null ? 0 : count;
    }

    private String participantName(long participantId) {
        return jdbc.queryForObject(
                "SELECT name_snapshot FROM training_participants WHERE id = ?",
                String.class,
                participantId
        );
    }

    private String participantNameByStudent(long sessionId, String studentNo) {
        return jdbc.queryForObject(
                "SELECT name_snapshot FROM training_participants WHERE session_id = ? AND student_no_snapshot = ?",
                String.class,
                sessionId,
                studentNo
        );
    }

    private BigDecimal participantDurationByStudent(long sessionId, String studentNo) {
        return jdbc.queryForObject(
                "SELECT duration_hours FROM training_participants WHERE session_id = ? AND student_no_snapshot = ?",
                BigDecimal.class,
                sessionId,
                studentNo
        );
    }

    private long requiredId(Long id) {
        if (id == null) {
            throw new IllegalStateException("测试管理员创建失败");
        }
        return id;
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("ca-training-transaction-test-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
