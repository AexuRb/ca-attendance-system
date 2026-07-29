package com.ca.attendance.attendance;

import com.ca.attendance.config.DatabaseMigrator;
import com.ca.attendance.config.SQLiteDataSourceConfiguration;
import com.ca.attendance.config.StoragePaths;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttendanceRepositoryIntegrationTest {
    @TempDir
    Path tempDirectory;

    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private AttendanceRepository records;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = (HikariDataSource) new SQLiteDataSourceConfiguration()
                .dataSource(new StoragePaths(tempDirectory.toString()));
        new DatabaseMigrator(dataSource).run();
        jdbc = new JdbcTemplate(dataSource);
        records = new AttendanceRepository(jdbc);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void searchForUserOnlyReturnsTheRequestedMembersRecords() {
        LocalDate date = LocalDate.of(2026, 7, 28);
        long targetId = insertUser("1001", "目标成员");
        long otherId = insertUser("1002", "其他成员");
        insertAttendance(targetId, "1001", "目标成员", date);
        insertAttendance(otherId, "1002", "其他成员", date);

        var result = records.searchForUser(targetId, date.minusDays(1), date.plusDays(1));

        assertEquals(1, result.size());
        assertEquals(targetId, result.getFirst().userId());
        assertEquals("1001", result.getFirst().studentNo());
    }

    @Test
    void searchPageAppliesFiltersAndReturnsStablePageMetadata() {
        LocalDate date = LocalDate.of(2026, 7, 28);
        long targetId = insertUser("1001", "目标成员");
        long otherId = insertUser("1002", "其他成员");
        insertAttendance(targetId, "1001", "目标成员", date.minusDays(1));
        insertAttendance(targetId, "1001", "目标成员", date);
        insertAttendance(otherId, "1002", "其他成员", date);

        var firstPage = records.searchPage(
                date.minusDays(1),
                date,
                "目标",
                "VALID",
                1,
                1
        );
        var secondPage = records.searchPage(
                date.minusDays(1),
                date,
                "目标",
                "VALID",
                2,
                1
        );
        var pagePastEnd = records.searchPage(
                date.minusDays(1),
                date,
                "目标",
                "VALID",
                9,
                1
        );

        assertEquals(2, firstPage.total());
        assertEquals(1, firstPage.page());
        assertEquals(1, firstPage.pageSize());
        assertEquals(date, firstPage.items().getFirst().dutyDate());
        assertEquals(date.minusDays(1), secondPage.items().getFirst().dutyDate());
        assertEquals(2, pagePastEnd.page());
        assertEquals(date.minusDays(1), pagePastEnd.items().getFirst().dutyDate());
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

    private void insertAttendance(long userId, String studentNo, String name, LocalDate date) {
        jdbc.update("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
                  check_in_time, check_out_time, check_in_status, check_out_status,
                  duration_minutes, valid_hours, effective_status
                )
                VALUES (?, ?, ?, ?, 2, ?, ?, 'APPROVED', 'APPROVED', 120, 2, 'VALID')
                """, userId, studentNo, name, date,
                Timestamp.valueOf(date.atTime(14, 0)),
                Timestamp.valueOf(date.atTime(16, 0)));
    }
}
