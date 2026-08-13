package com.ca.attendance.training;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.Role;
import com.ca.attendance.config.DatabaseMigrator;
import com.ca.attendance.config.SQLiteDataSourceConfiguration;
import com.ca.attendance.config.StoragePaths;
import com.ca.attendance.log.OperationLogService;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TrainingPaginationIntegrationTest {
    @TempDir
    Path tempDirectory;

    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private TrainingService trainings;
    private long adminId;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = (HikariDataSource) new SQLiteDataSourceConfiguration()
                .dataSource(new StoragePaths(tempDirectory.toString()));
        new DatabaseMigrator(dataSource).run();
        jdbc = new JdbcTemplate(dataSource);
        adminId = insertUser("pagination-admin", "分页测试管理员", "ADMIN");
        AuthContext.set(new AuthUser(
                adminId,
                "pagination-admin",
                "分页测试管理员",
                Role.ADMIN,
                Instant.now().plusSeconds(3600)
        ));
        trainings = new TrainingService(jdbc, mock(OperationLogService.class));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void sessionPageReturnsRequestedSliceWithFullAggregates() {
        LocalDate start = LocalDate.of(2026, 8, 1);
        List<Long> sessionIds = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            sessionIds.add(insertSession("分页场次 " + index, start.plusDays(index), "主讲人 " + index));
        }
        long thirdSession = sessionIds.get(2);
        insertParticipant(thirdSession, "300001", "主讲人 3", "1.50", "主讲人");
        insertParticipant(thirdSession, "300002", "零时长成员", "0.00", "旁听");

        TrainingService.TrainingSessionPage result = trainings.page(
                "分页场次",
                null,
                start,
                start.plusDays(6),
                2,
                2
        );

        assertEquals(5, result.total());
        assertEquals(2, result.page());
        assertEquals(2, result.pageSize());
        assertTrue(result.hasMore());
        assertEquals(List.of("分页场次 3", "分页场次 2"), result.items().stream()
                .map(TrainingSessionItem::title)
                .toList());
        assertEquals(1, result.items().getFirst().participantCount());
        assertEquals(0, new BigDecimal("1.50").compareTo(result.items().getFirst().totalDurationHours()));
    }

    @Test
    void sessionPageNormalizesPageBounds() {
        LocalDate date = LocalDate.of(2026, 8, 10);
        insertSession("边界场次", date, "边界主讲人");

        TrainingService.TrainingSessionPage result = trainings.page(
                null,
                null,
                date,
                date,
                0,
                500
        );

        assertEquals(1, result.page());
        assertEquals(100, result.pageSize());
        assertEquals(1, result.items().size());
        assertFalse(result.hasMore());

        TrainingService.TrainingSessionPage distantPage = trainings.page(
                null,
                null,
                date,
                date,
                Integer.MAX_VALUE,
                20
        );
        assertTrue(distantPage.items().isEmpty());
        assertFalse(distantPage.hasMore());
    }

    @Test
    void participantPagePaginatesInSpeakerFirstOrderAndSearchesVisibleFields() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        long sessionId = insertSession("名单分页场次", date, "培训主讲人");
        insertParticipant(sessionId, "400003", "普通成员三", "2.00", "重点实操");
        insertParticipant(sessionId, "400001", "普通成员一", "2.00", null);
        insertParticipant(sessionId, "499999", "培训主讲人", "2.00", "主讲人");
        insertParticipant(sessionId, "400002", "目标成员", "2.00", null);
        insertParticipant(sessionId, "400004", "普通成员四", "2.00", "重点答疑");

        TrainingService.TrainingParticipantPage firstPage = trainings.participantPage(
                sessionId,
                null,
                1,
                2
        );
        TrainingService.TrainingParticipantPage secondPage = trainings.participantPage(
                sessionId,
                null,
                2,
                2
        );

        assertEquals(5, firstPage.total());
        assertEquals(List.of("培训主讲人", "普通成员一"), firstPage.items().stream()
                .map(TrainingParticipantItem::name)
                .toList());
        assertEquals(List.of("目标成员", "普通成员三"), secondPage.items().stream()
                .map(TrainingParticipantItem::name)
                .toList());
        assertTrue(secondPage.hasMore());

        assertEquals(1, trainings.participantPage(sessionId, "目标", 1, 30).total());
        assertEquals(1, trainings.participantPage(sessionId, "400001", 1, 30).total());
        assertEquals(2, trainings.participantPage(sessionId, "重点", 1, 30).total());
    }

    @Test
    void largeCollectionsRemainBoundedByRequestedPageSizeAndCompleteInExport() throws Exception {
        LocalDate start = LocalDate.of(2025, 1, 1);
        List<Object[]> sessionRows = new ArrayList<>();
        for (int index = 1; index <= 205; index++) {
            sessionRows.add(new Object[]{
                    "规模场次 " + index,
                    start.plusDays(index),
                    "规模主讲人 " + index,
                    adminId,
                    adminId
            });
        }
        jdbc.batchUpdate("""
                INSERT INTO training_sessions (
                  title, training_date, start_time, end_time, location, speaker, status,
                  created_by, updated_by
                )
                VALUES (?, ?, '14:00:00', '16:00:00', '规模测试地点', ?, 'COMPLETED', ?, ?)
                """, sessionRows);
        Long targetId = jdbc.queryForObject(
                "SELECT id FROM training_sessions WHERE title = '规模场次 205'",
                Long.class
        );
        List<Object[]> participantRows = new ArrayList<>();
        for (int index = 1; index <= 3000; index++) {
            participantRows.add(new Object[]{
                    targetId,
                    String.format("8%07d", index),
                    "规模成员 " + index,
                    adminId,
                    adminId
            });
        }
        jdbc.batchUpdate("""
                INSERT INTO training_participants (
                  session_id, student_no_snapshot, name_snapshot, attendance_status,
                  duration_hours, source, created_by, updated_by
                )
                VALUES (?, ?, ?, 'PRESENT', 2.00, 'MANUAL', ?, ?)
                """, participantRows);

        TrainingService.TrainingSessionPage sessionPage = trainings.page(
                "规模场次",
                null,
                start,
                start.plusDays(206),
                1,
                20
        );
        TrainingService.TrainingParticipantPage participantPage = trainings.participantPage(
                targetId == null ? 0 : targetId,
                null,
                1,
                30
        );

        assertEquals(205, sessionPage.total());
        assertEquals(20, sessionPage.items().size());
        assertTrue(sessionPage.hasMore());
        assertEquals(3000, participantPage.total());
        assertEquals(30, participantPage.items().size());
        assertTrue(participantPage.hasMore());

        TrainingService.ExportFile export = trainings.exportSession(targetId == null ? 0 : targetId);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(export.bytes()))) {
            assertEquals(3000, workbook.getSheet("培训名单").getLastRowNum() - 3);
        }
    }

    private long insertUser(String studentNo, String name, String role) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (
                  student_no, name, password_hash, role, status, must_change_password
                )
                VALUES (?, ?, 'test-hash', ?, 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name, role);
        return id == null ? 0 : id;
    }

    private long insertSession(String title, LocalDate date, String speaker) {
        Long id = jdbc.queryForObject("""
                INSERT INTO training_sessions (
                  title, training_date, start_time, end_time, location, speaker, status,
                  created_by, updated_by
                )
                VALUES (?, ?, '14:00:00', '16:00:00', '分页测试地点', ?, 'COMPLETED', ?, ?)
                RETURNING id
                """, Long.class, title, date, speaker, adminId, adminId);
        return id == null ? 0 : id;
    }

    private void insertParticipant(
            long sessionId,
            String studentNo,
            String name,
            String durationHours,
            String remark
    ) {
        jdbc.update("""
                INSERT INTO training_participants (
                  session_id, student_no_snapshot, name_snapshot, attendance_status,
                  duration_hours, remark, source, created_by, updated_by
                )
                VALUES (?, ?, ?, 'PRESENT', ?, ?, 'MANUAL', ?, ?)
                """, sessionId, studentNo, name, durationHours, remark, adminId, adminId);
    }
}
