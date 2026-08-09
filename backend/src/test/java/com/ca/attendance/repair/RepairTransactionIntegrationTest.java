package com.ca.attendance.repair;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RepairTransactionIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final DateTimeFormatter CASE_DAY = DateTimeFormatter.BASIC_ISO_DATE;

    @Autowired
    private RepairCaseService repairs;

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
        jdbc.update("DELETE FROM repair_cases WHERE owner_name LIKE '事务维修%'");

        List<Long> existingAdminIds = jdbc.queryForList(
                "SELECT id FROM users WHERE student_no = 'tx-repair-admin'",
                Long.class
        );
        adminId = existingAdminIds.isEmpty()
                ? requiredId(jdbc.queryForObject("""
                    INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                    VALUES ('tx-repair-admin', '维修事务管理员', 'test-hash', 'ADMIN', 'ACTIVE', 0)
                    RETURNING id
                    """, Long.class))
                : existingAdminIds.getFirst();
        AuthContext.set(new AuthUser(
                adminId,
                "tx-repair-admin",
                "维修事务管理员",
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
    void createRollsBackCaseAndSequenceWhenAuditLogFails() {
        int sequenceBefore = todaySequence();
        failAudit("CREATE_REPAIR_CASE");

        assertThrows(DataAccessException.class, () -> repairs.create(repairRequest("事务维修新增")));

        assertEquals(0, repairCount("事务维修新增"));
        assertEquals(sequenceBefore, todaySequence());
    }

    @Test
    void updateRollsBackWhenAuditLogFails() {
        RepairCaseItem repair = repairs.create(repairRequest("事务维修修改前"));
        failAudit("UPDATE_REPAIR_CASE");

        assertThrows(DataAccessException.class, () -> repairs.update(
                repair.id(),
                repairRequest("事务维修修改后")
        ));

        assertEquals("事务维修修改前", ownerName(repair.id()));
    }

    private RepairCaseService.RepairCaseRequest repairRequest(String ownerName) {
        return new RepairCaseService.RepairCaseRequest(
                "PERSONAL_DEVICE",
                ownerName,
                "13800000000",
                null,
                "笔记本电脑",
                "测试品牌",
                "测试型号",
                null,
                "电源适配器",
                "无法开机",
                null,
                true,
                true,
                true,
                "REPAIRING",
                LocalDateTime.now(),
                null,
                "维修事务管理员",
                null
        );
    }

    private void failAudit(String actionType) {
        dropAuditTrigger();
        jdbc.execute("""
                CREATE TRIGGER fail_repair_audit
                BEFORE INSERT ON operation_logs
                WHEN NEW.action_type = '%s'
                BEGIN
                  SELECT RAISE(ABORT, 'forced audit failure');
                END
                """.formatted(actionType));
    }

    private void dropAuditTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_repair_audit");
    }

    private int repairCount(String ownerName) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM repair_cases WHERE owner_name = ?",
                Integer.class,
                ownerName
        );
    }

    private String ownerName(long id) {
        return jdbc.queryForObject("SELECT owner_name FROM repair_cases WHERE id = ?", String.class, id);
    }

    private int todaySequence() {
        List<Integer> values = jdbc.queryForList(
                "SELECT last_value FROM repair_case_sequences WHERE sequence_date = ?",
                Integer.class,
                LocalDate.now().format(CASE_DAY)
        );
        return values.isEmpty() ? 0 : values.getFirst();
    }

    private long requiredId(Long id) {
        if (id == null) {
            throw new IllegalStateException("测试管理员创建失败");
        }
        return id;
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("ca-repair-transaction-test-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
