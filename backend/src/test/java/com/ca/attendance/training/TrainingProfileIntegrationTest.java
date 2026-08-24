package com.ca.attendance.training;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.Role;
import com.ca.attendance.config.DatabaseMigrator;
import com.ca.attendance.config.SQLiteDataSourceConfiguration;
import com.ca.attendance.config.StoragePaths;
import com.ca.attendance.log.OperationLogService;
import tools.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class TrainingProfileIntegrationTest {
    @TempDir
    Path tempDirectory;

    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private TrainingService trainings;
    private long memberId;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = (HikariDataSource) new SQLiteDataSourceConfiguration()
                .dataSource(new StoragePaths(tempDirectory.toString()));
        new DatabaseMigrator(dataSource).run();
        jdbc = new JdbcTemplate(dataSource);
        memberId = insertUser("1001", "目标成员");
        AuthContext.set(new AuthUser(
                memberId,
                "1001",
                "目标成员",
                Role.MEMBER,
                Instant.now().plusSeconds(3600)
        ));
        trainings = new TrainingService(
                jdbc,
                mock(OperationLogService.class),
                new TrainingQueryService(jdbc),
                new TrainingExcelExportService(),
                new TrainingParticipantImportParser()
        );
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void myRecordsReturnsSessionDetailsAndExcludesOtherMembers() {
        LocalDate date = LocalDate.of(2026, 7, 28);
        long otherId = insertUser("1002", "其他成员");
        long sessionId = insertSession(date);
        insertParticipant(sessionId, memberId, "1001", "目标成员", "PRESENT", "1.50");
        insertParticipant(sessionId, otherId, "1002", "其他成员", "PRESENT", "2.00");

        var result = trainings.myRecords(date.minusDays(1), date.plusDays(1));

        assertEquals(1, result.size());
        assertEquals("网络基础培训", result.getFirst().title());
        assertEquals(0, new BigDecimal("1.50").compareTo(result.getFirst().durationHours()));
        assertFalse(objectMapper().valueToTree(result.getFirst()).has("attendanceStatus"));
    }

    @Test
    void participantAndSessionResponsesExposeDurationWithoutInternalMetadata() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 29);
        long adminId = insertAdmin("admin-training", "培训管理员");
        long zeroHoursMemberId = insertUser("1003", "零时长成员");
        long sessionId = insertSession(date);
        insertParticipant(sessionId, memberId, "1001", "目标成员", "LEAVE", "1.25");
        insertParticipant(sessionId, zeroHoursMemberId, "1003", "零时长成员", "ABSENT", "0.00");
        AuthContext.set(new AuthUser(
                adminId,
                "admin-training",
                "培训管理员",
                Role.ADMIN,
                Instant.now().plusSeconds(3600)
        ));

        var participantJson = objectMapper().valueToTree(
                trainings.participantPage(sessionId, null, 1, 30).items().getFirst()
        );
        var sessionJson = objectMapper().valueToTree(
                trainings.page(null, null, date, date, 1, 20).items().getFirst()
        );

        assertEquals(1.25, participantJson.get("durationHours").asDouble());
        assertFalse(participantJson.has("attendanceStatus"));
        assertFalse(participantJson.has("source"));
        assertEquals(1, sessionJson.get("participantCount").asInt());
        assertEquals(1.25, sessionJson.get("totalDurationHours").asDouble());
        assertFalse(sessionJson.has("presentCount"));
        assertFalse(sessionJson.has("absentCount"));
        assertFalse(sessionJson.has("leaveCount"));

        TrainingService.ExportFile summary = trainings.exportSummary(null, null, date, date);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(summary.bytes()))) {
            var sessionRow = workbook.getSheet("培训场次").getRow(3);
            assertEquals(1.0, sessionRow.getCell(4).getNumericCellValue());
            assertEquals(1.25, sessionRow.getCell(5).getNumericCellValue());
            var memberRow = workbook.getSheet("成员统计").getRow(1);
            assertEquals("1001", memberRow.getCell(0).getStringCellValue());
            assertEquals(1.0, memberRow.getCell(2).getNumericCellValue());
            assertEquals(1.25, memberRow.getCell(3).getNumericCellValue());
        }
    }

    private long insertUser(String studentNo, String name) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (
                  student_no, name, password_hash, role, status, must_change_password
                )
                VALUES (?, ?, 'test-hash', 'MEMBER', 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name);
        return id == null ? 0 : id;
    }

    private long insertAdmin(String studentNo, String name) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (
                  student_no, name, password_hash, role, status, must_change_password
                )
                VALUES (?, ?, 'test-hash', 'ADMIN', 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name);
        return id == null ? 0 : id;
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    private long insertSession(LocalDate date) {
        Long id = jdbc.queryForObject("""
                INSERT INTO training_sessions (
                  title, training_date, start_time, end_time, location, speaker, status
                )
                VALUES ('网络基础培训', ?, '14:00:00', '16:00:00', '活动室', '主讲人', 'COMPLETED')
                RETURNING id
                """, Long.class, date);
        return id == null ? 0 : id;
    }

    private void insertParticipant(
            long sessionId,
            long userId,
            String studentNo,
            String name,
            String status,
            String hours
    ) {
        jdbc.update("""
                INSERT INTO training_participants (
                  session_id, user_id, student_no_snapshot, name_snapshot,
                  attendance_status, duration_hours
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """, sessionId, userId, studentNo, name, status, hours);
    }
}
