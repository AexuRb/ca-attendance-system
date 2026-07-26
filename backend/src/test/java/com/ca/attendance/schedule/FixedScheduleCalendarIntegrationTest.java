package com.ca.attendance.schedule;

import com.ca.attendance.config.DatabaseMigrator;
import com.ca.attendance.config.SQLiteDataSourceConfiguration;
import com.ca.attendance.config.StoragePaths;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.schedule.application.FixedScheduleCalendarService;
import com.ca.attendance.schedule.domain.FixedScheduleDay;
import com.ca.attendance.settings.DutyPeriodService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FixedScheduleCalendarIntegrationTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);

    @TempDir
    Path tempDirectory;

    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private FixedScheduleCalendarService calendar;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = (HikariDataSource) new SQLiteDataSourceConfiguration()
                .dataSource(new StoragePaths(tempDirectory.toString()));
        new DatabaseMigrator(dataSource).run();
        jdbc = new JdbcTemplate(dataSource);

        long presidentId = insertUser("president", "会长", "PRESIDENT");
        insertUser("m001", "部长甲", "MINISTER");
        insertUser("m002", "部长乙", "MINISTER");
        long slotId = insertSlot(presidentId);
        insertAssignee(slotId, "m001", 0);
        insertAssignee(slotId, "m002", 1);

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        OperationLogService logs = new OperationLogService(jdbc, objectMapper);
        DutyPeriodService periods = new DutyPeriodService(jdbc, objectMapper, logs);
        calendar = new FixedScheduleCalendarService(new DutyScheduleService(jdbc, logs, periods));
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void mapsTheFixedWeeklyScheduleToTheRequestedDate() {
        FixedScheduleDay day = calendar.day(MONDAY);

        assertEquals(MONDAY, day.date());
        assertEquals("星期一", day.weekdayName());
        assertEquals(1, day.slots().size());
        assertEquals("部长值班", day.slots().getFirst().title());
        assertEquals(List.of("部长甲", "部长乙"), day.slots().getFirst().assignees().stream()
                .map(FixedScheduleDay.Assignee::name)
                .toList());
    }

    @Test
    void returnsASevenDayWeekWithoutAdjustmentState() {
        List<FixedScheduleDay> week = calendar.week(MONDAY);

        assertEquals(7, week.size());
        assertEquals(MONDAY, week.getFirst().date());
        assertFalse(week.getFirst().slots().isEmpty());
        assertEquals(MONDAY.plusDays(6), week.getLast().date());
    }

    private long insertSlot(long actorId) {
        Long id = jdbc.queryForObject("""
                INSERT INTO duty_schedule_slots (
                  weekday, start_time, end_time, title, location, enabled, status, created_by, updated_by
                ) VALUES (1, '14:00:00', '16:00:00', '部长值班', '协会办公室', 1, 'ACTIVE', ?, ?)
                RETURNING id
                """, Long.class, actorId, actorId);
        return id == null ? 0 : id;
    }

    private void insertAssignee(long slotId, String studentNo, int sortOrder) {
        jdbc.update("""
                INSERT INTO duty_schedule_assignees (
                  slot_id, user_id, student_no_snapshot, name_snapshot, sort_order
                )
                SELECT ?, id, student_no, name, ? FROM users WHERE student_no = ?
                """, slotId, sortOrder, studentNo);
    }

    private long insertUser(String studentNo, String name, String role) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES (?, ?, 'test-hash', ?, 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name, role);
        return id == null ? 0 : id;
    }
}
