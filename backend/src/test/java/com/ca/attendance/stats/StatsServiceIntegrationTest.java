package com.ca.attendance.stats;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.Role;
import com.ca.attendance.config.DatabaseMigrator;
import com.ca.attendance.config.SQLiteDataSourceConfiguration;
import com.ca.attendance.config.StoragePaths;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        stats = new StatsService(jdbc);
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
                  session_id, user_id, student_no_snapshot, name_snapshot, duration_hours
                )
                VALUES (?, ?, 'member', '测试成员', 1.5)
                """, sessionId, memberId);

        List<Map<String, Object>> result = stats.summary(date, date);

        assertEquals(1, result.size());
        Map<String, Object> row = result.getFirst();
        assertEquals("MEMBER", row.get("role"));
        assertEquals(1, ((Number) row.get("attendanceCount")).intValue());
        assertEquals(1, ((Number) row.get("trainingCount")).intValue());
        assertEquals(new BigDecimal("2.00"), row.get("attendanceHours"));
        assertEquals(new BigDecimal("1.50"), row.get("trainingHours"));
        assertEquals(new BigDecimal("3.50"), row.get("totalHours"));
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
