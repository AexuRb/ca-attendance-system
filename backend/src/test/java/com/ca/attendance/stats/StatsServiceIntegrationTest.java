package com.ca.attendance.stats;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.config.DatabaseMigrator;
import com.ca.attendance.config.SQLiteDataSourceConfiguration;
import com.ca.attendance.config.StoragePaths;
import com.ca.attendance.log.OperationLogService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatsServiceIntegrationTest {
    @TempDir
    Path tempDirectory;

    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private StatsService stats;
    private long memberId;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = (HikariDataSource) new SQLiteDataSourceConfiguration()
                .dataSource(new StoragePaths(tempDirectory.toString()));
        new DatabaseMigrator(dataSource).run();
        jdbc = new JdbcTemplate(dataSource);

        long adminId = insertUser("admin", "管理员", "ADMIN");
        memberId = insertUser("member", "测试成员", "MEMBER");
        AuthContext.set(new AuthUser(
                adminId,
                "admin",
                "管理员",
                Role.ADMIN,
                Instant.now().plusSeconds(3600)
        ));
        stats = new StatsService(jdbc, new OperationLogService(jdbc, new ObjectMapper()));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void summarySeparatesAttendanceAndTrainingTotals() {
        LocalDate date = LocalDate.of(2026, 7, 24);
        jdbc.update("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                  check_in_time, check_out_time, check_in_status, check_out_status,
                  duration_minutes, valid_hours, effective_status
                )
                VALUES (?, 'member', '测试成员', ?, 5, ?, ?, 'APPROVED', 'APPROVED', 120, 2, 'VALID')
                """, memberId, date, date.atTime(14, 0), date.atTime(16, 0));
        long sessionId = requiredId(jdbc.queryForObject("""
                INSERT INTO training_sessions (
                  title, training_date, start_time, end_time, status
                )
                VALUES ('统计测试培训', ?, '16:00:00', '17:30:00', 'COMPLETED')
                RETURNING id
                """, Long.class, date));
        jdbc.update("""
                INSERT INTO training_participants (
                  session_id, user_id, student_no_snapshot, name_snapshot, attendance_status, duration_hours
                )
                VALUES (?, ?, 'member', '测试成员', 'ABSENT', 1.5)
                """, sessionId, memberId);
        jdbc.update("""
                INSERT INTO training_participants (
                  session_id, user_id, student_no_snapshot, name_snapshot, attendance_status, duration_hours
                )
                VALUES (?, NULL, 'zero-hours', '零时长记录', 'LEAVE', 0)
                """, sessionId);

        List<StatsService.SummaryItem> result = stats.summary(date, date);

        assertEquals(1, result.size());
        StatsService.SummaryItem row = result.getFirst();
        assertEquals("MEMBER", row.role());
        assertEquals(1, row.attendanceCount());
        assertEquals(1, row.trainingCount());
        assertEquals(new BigDecimal("2.00"), row.attendanceHours());
        assertEquals(new BigDecimal("1.50"), row.trainingHours());
        assertEquals(new BigDecimal("3.50"), row.totalHours());
    }

    @Test
    void summaryDoesNotExposePrivateMemberFieldsToMinisters() {
        LocalDate date = LocalDate.of(2026, 7, 24);
        jdbc.update("""
                UPDATE users
                SET phone = '13800000000', major = '测试学院', qq = '123456'
                WHERE id = ?
                """, memberId);
        jdbc.update("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                  check_in_time, check_out_time, check_in_status, check_out_status,
                  duration_minutes, valid_hours, effective_status
                )
                VALUES (?, 'member', '测试成员', ?, 5, ?, ?, 'APPROVED', 'APPROVED', 60, 1, 'VALID')
                """, memberId, date, date.atTime(14, 0), date.atTime(15, 0));
        AuthContext.set(new AuthUser(
                memberId,
                "minister",
                "测试部长",
                Role.MINISTER,
                Instant.now().plusSeconds(3600)
        ));

        Object resultRow = stats.summary(date, date).getFirst();
        Map<String, Object> payload = new ObjectMapper().convertValue(
                resultRow,
                new TypeReference<>() {
                }
        );

        assertFalse(payload.containsKey("phone"));
        assertFalse(payload.containsKey("major"));
        assertFalse(payload.containsKey("qq"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void weeklyDetailSeparatesDailyAttendanceFromTrainingAndIncludesMemberContext() {
        LocalDate date = LocalDate.of(2026, 7, 24);
        jdbc.update("UPDATE users SET grade = '2025级' WHERE id = ?", memberId);
        jdbc.update("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                  check_in_time, check_out_time, check_in_status, check_out_status,
                  duration_minutes, valid_hours, effective_status
                )
                VALUES (?, 'member', '测试成员', ?, 5, ?, ?, 'APPROVED', 'APPROVED', 120, 2, 'VALID')
                """, memberId, date, date.atTime(14, 0), date.atTime(16, 0));
        long sessionId = requiredId(jdbc.queryForObject("""
                INSERT INTO training_sessions (
                  title, training_date, start_time, end_time, status
                )
                VALUES ('周统计培训', ?, '16:00:00', '17:30:00', 'COMPLETED')
                RETURNING id
                """, Long.class, date));
        jdbc.update("""
                INSERT INTO training_participants (
                  session_id, user_id, student_no_snapshot, name_snapshot, duration_hours
                )
                VALUES (?, ?, 'member', '测试成员', 1.5)
                """, sessionId, memberId);

        Map<String, Object> result = stats.weeklyDetail(date, date);
        List<Map<String, Object>> users = (List<Map<String, Object>>) result.get("users");
        Map<String, Map<String, BigDecimal>> cells =
                (Map<String, Map<String, BigDecimal>>) result.get("cells");

        assertEquals(1, users.size());
        Map<String, Object> row = users.getFirst();
        assertEquals("2025级", row.get("grade"));
        assertEquals("MEMBER", row.get("role"));
        assertEquals(new BigDecimal("2.00"), row.get("attendanceHours"));
        assertEquals(new BigDecimal("1.50"), row.get("trainingHours"));
        assertEquals(new BigDecimal("3.50"), row.get("totalHours"));
        assertEquals(new BigDecimal("2.00"), cells.get(date.toString()).get(String.valueOf(memberId)));
    }

    @Test
    void allRangeEndpointsRejectReversedDates() {
        LocalDate from = LocalDate.of(2026, 8, 2);
        LocalDate to = LocalDate.of(2026, 8, 1);

        assertThrows(ApiException.class, () -> stats.summary(from, to));
        assertThrows(ApiException.class, () -> stats.weeklyDetail(from, to));
        assertThrows(ApiException.class, () -> stats.export(from, to));
    }

    @Test
    void allRangeEndpointsRejectMoreThan366InclusiveDays() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 2);

        assertThrows(ApiException.class, () -> stats.summary(from, to));
        assertThrows(ApiException.class, () -> stats.weeklyDetail(from, to));
        assertThrows(ApiException.class, () -> stats.export(from, to));
    }

    @Test
    void fullCalendarYearRemainsAvailable() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 12, 31);

        assertDoesNotThrow(() -> stats.summary(from, to));
        assertDoesNotThrow(() -> stats.weeklyDetail(from, to));
    }

    @Test
    void exportUsesStableReadableWidthsAndKeepsExcelTotalFormula() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 24);
        jdbc.update("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                  check_in_time, check_out_time, check_in_status, check_out_status,
                  duration_minutes, valid_hours, effective_status
                )
                VALUES (?, 'member', '测试成员', ?, 5, ?, ?, 'APPROVED', 'APPROVED', 120, 2, 'VALID')
                """, memberId, date, date.atTime(14, 0), date.atTime(16, 0));

        byte[] bytes = stats.export(date, date).bytes();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("值班记录");
            assertEquals(18 * 256, sheet.getColumnWidth(0));
            assertEquals(14 * 256, sheet.getColumnWidth(1));
            assertEquals(16 * 256, sheet.getColumnWidth(2));
            assertEquals(14 * 256, sheet.getColumnWidth(3));
            assertEquals("SUM(C3:C3)", sheet.getRow(2).getCell(3).getCellFormula());
            assertEquals(true, workbook.getForceFormulaRecalculation());
        }
    }

    private long insertUser(String studentNo, String name, String role) {
        return requiredId(jdbc.queryForObject("""
                INSERT INTO users (
                  student_no, name, password_hash, role, status, must_change_password
                )
                VALUES (?, ?, 'test-hash', ?, 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name, role));
    }

    private long requiredId(Long value) {
        return value == null ? 0 : value;
    }
}
