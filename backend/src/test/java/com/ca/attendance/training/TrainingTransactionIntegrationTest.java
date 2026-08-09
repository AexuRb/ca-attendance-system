package com.ca.attendance.training;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
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
                "PRESENT",
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

    private String participantName(long participantId) {
        return jdbc.queryForObject(
                "SELECT name_snapshot FROM training_participants WHERE id = ?",
                String.class,
                participantId
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
