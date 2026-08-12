package com.ca.attendance.repair;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.config.DatabaseMigrator;
import com.ca.attendance.config.SQLiteDataSourceConfiguration;
import com.ca.attendance.config.StoragePaths;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.maintenance.BackupService;
import com.ca.attendance.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairPaginationIntegrationTest {
    @TempDir
    Path tempDirectory;

    private HikariDataSource dataSource;
    private RepairCaseService repairs;
    private long adminId;
    private LocalDate date;

    @BeforeEach
    void setUp() throws Exception {
        StoragePaths paths = new StoragePaths(tempDirectory.toString());
        dataSource = (HikariDataSource) new SQLiteDataSourceConfiguration().dataSource(paths);
        new DatabaseMigrator(dataSource).run();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        OperationLogService logs = new OperationLogService(jdbc, objectMapper);
        BackupService backups = new BackupService(
                jdbc,
                objectMapper,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new TokenService(12),
                paths
        );
        repairs = new RepairCaseService(jdbc, logs, backups, new UserRepository(jdbc));
        adminId = requiredId(jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('repair-page-admin', '分页测试管理员', 'test-hash', 'ADMIN', 'ACTIVE', 0)
                RETURNING id
                """, Long.class));
        AuthContext.set(new AuthUser(
                adminId,
                "repair-page-admin",
                "分页测试管理员",
                Role.ADMIN,
                Instant.now().plusSeconds(3600)
        ));
        date = LocalDate.of(2026, 8, 12);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void pagesEachBoardStatusIndependentlyWithStableOrder() {
        insertCases("REPAIRING", 35, "R");
        insertCases("COMPLETED", 4, "D");
        insertCases("CANCELED", 2, "C");

        RepairCaseService.RepairPage first = repairs.page(null, "REPAIRING", date, date, 1, 30);
        RepairCaseService.RepairPage second = repairs.page(null, "REPAIRING", date, date, 2, 30);
        RepairCaseService.RepairPage completed = repairs.page(null, "COMPLETED", date, date, 1, 30);
        RepairCaseService.RepairPage canceled = repairs.page(null, "CANCELED", date, date, 1, 30);

        assertEquals(35, first.total());
        assertEquals(30, first.items().size());
        assertEquals(1, first.page());
        assertEquals(30, first.pageSize());
        assertTrue(first.hasMore());
        assertEquals(5, second.items().size());
        assertFalse(second.hasMore());
        HashSet<Long> ids = new HashSet<>(first.items().stream().map(RepairCaseItem::id).toList());
        ids.addAll(second.items().stream().map(RepairCaseItem::id).toList());
        assertEquals(35, ids.size());
        assertTrue(first.items().getFirst().receivedAt().isAfter(first.items().getLast().receivedAt()));
        assertEquals(4, completed.total());
        assertTrue(completed.items().stream().allMatch(item -> "COMPLETED".equals(item.status())));
        assertEquals(2, canceled.total());
        assertTrue(canceled.items().stream().allMatch(item -> "CANCELED".equals(item.status())));
    }

    @Test
    void pageAppliesFiltersAndRejectsInvalidRangesOrStatuses() {
        insertCases("REPAIRING", 3, "MATCH");
        insertCases("COMPLETED", 2, "OTHER");

        RepairCaseService.RepairPage matched = repairs.page("MATCH-2", "REPAIRING", date, date, 0, 500);

        assertEquals(1, matched.total());
        assertEquals(1, matched.page());
        assertEquals(100, matched.pageSize());
        assertEquals("MATCH-2", matched.items().getFirst().ownerName());
        assertThrows(ApiException.class,
                () -> repairs.page(null, "ALL", date, date, 1, 30));
        assertThrows(ApiException.class,
                () -> repairs.page(null, "REPAIRING", date.plusDays(1), date, 1, 30));
    }

    @Test
    void thousandRowsStayPagedOnBoardButRemainCompleteInExport() throws Exception {
        insertCases("REPAIRING", 334, "R");
        insertCases("COMPLETED", 333, "D");
        insertCases("CANCELED", 333, "C");

        RepairCaseService.RepairPage boardPage = repairs.page(null, "REPAIRING", date, date, 1, 30);
        RepairCaseService.ExportFile export = repairs.exportCases(null, "ALL", date, date);

        assertEquals(30, boardPage.items().size());
        assertEquals(334, boardPage.total());
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(export.bytes()))) {
            assertEquals(1000, workbook.getSheet("维修事务").getLastRowNum() - 2);
        }
    }

    private void insertCases(String status, int count, String prefix) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (int index = 1; index <= count; index++) {
            jdbc.update("""
                    INSERT INTO repair_cases (
                      case_no, agreement_type, owner_name, device_type, fault_description,
                      status, received_at, created_by, updated_by
                    )
                    VALUES (?, 'PERSONAL_DEVICE', ?, '笔记本电脑', '分页性能测试', ?, ?, ?, ?)
                    """,
                    "PAGE-" + prefix + "-" + index,
                    prefix + "-" + index,
                    status,
                    Timestamp.valueOf(date.atTime(8, 0).plusMinutes(index)),
                    adminId,
                    adminId
            );
        }
    }

    private long requiredId(Long value) {
        assertNotNull(value);
        return value;
    }
}
