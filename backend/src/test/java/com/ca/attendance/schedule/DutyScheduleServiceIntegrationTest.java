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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.Instant;
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
    private long ministerId;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = (HikariDataSource) new SQLiteDataSourceConfiguration()
                .dataSource(new StoragePaths(tempDirectory.toString()));
        new DatabaseMigrator(dataSource).run();
        jdbc = new JdbcTemplate(dataSource);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OperationLogService logs = new OperationLogService(jdbc, objectMapper);
        long adminId = insertUser("1000", "管理员", "ADMIN", "ACTIVE");
        ministerId = insertUser("1001", "张部长", "MINISTER", "ACTIVE");
        insertUser("1002", "停用部长", "MINISTER", "DISABLED");
        insertUser("1003", "普通成员", "MEMBER", "ACTIVE");
        AuthContext.set(new AuthUser(adminId, "1000", "管理员", Role.ADMIN, Instant.now().plusSeconds(3600)));

        periods = new DutyPeriodService(jdbc, objectMapper, logs);
        periods.update(List.of(new DutyPeriodService.DutyPeriodRequest("14:00", "16:00")));
        schedules = new DutyScheduleService(jdbc, logs, periods);
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

    private DutyScheduleService.SlotRequest request(String studentNo) {
        return new DutyScheduleService.SlotRequest(
                1,
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
