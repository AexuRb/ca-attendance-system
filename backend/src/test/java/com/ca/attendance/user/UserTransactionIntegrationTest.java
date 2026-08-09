package com.ca.attendance.user;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserTransactionIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    private long adminId;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_bulk_status");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_delete_log");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_create_user_log");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_reset_password_log");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_import_users_log");
        jdbc.update("DELETE FROM operation_logs");
        jdbc.update("DELETE FROM users WHERE student_no LIKE 'tx-%'");

        List<Long> existingAdminIds = jdbc.queryForList(
                "SELECT id FROM users WHERE student_no = 'tx-admin'",
                Long.class
        );
        Long existingAdminId = existingAdminIds.isEmpty()
                ? jdbc.queryForObject("""
                    INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                    VALUES ('tx-admin', '事务测试管理员', 'test-hash', 'ADMIN', 'ACTIVE', 0)
                    RETURNING id
                    """, Long.class)
                : existingAdminIds.getFirst();
        adminId = existingAdminId;
        AuthContext.set(new AuthUser(
                adminId,
                "tx-admin",
                "事务测试管理员",
                Role.ADMIN,
                Instant.now().plusSeconds(3600)
        ));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        jdbc.execute("DROP TRIGGER IF EXISTS fail_bulk_status");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_delete_log");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_create_user_log");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_reset_password_log");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_import_users_log");
    }

    @Test
    void bulkStatusRollsBackAllUsersWhenOneUpdateFails() {
        long firstId = insertMember("tx-bulk-first", "批量成员一");
        long secondId = insertMember("tx-bulk-second", "批量成员二");
        jdbc.execute("""
                CREATE TRIGGER fail_bulk_status
                BEFORE UPDATE OF status ON users
                WHEN OLD.student_no = 'tx-bulk-second'
                BEGIN
                  SELECT RAISE(ABORT, 'forced bulk failure');
                END
                """);

        assertThrows(DataAccessException.class, () -> users.bulkStatus(new UserService.BulkStatusRequest(
                List.of(firstId, secondId),
                null,
                null,
                null,
                null,
                "DISABLED",
                "事务回滚测试"
        )));

        assertEquals("ACTIVE", status(firstId));
        assertEquals("ACTIVE", status(secondId));
        assertEquals(0, actionCount("BULK_UPDATE_USER_STATUS"));
    }

    @Test
    void deleteRollsBackMemberWhenAuditLogFails() {
        long targetId = insertMember("tx-delete-target", "删除事务成员");
        jdbc.execute("""
                CREATE TRIGGER fail_delete_log
                BEFORE INSERT ON operation_logs
                WHEN NEW.action_type = 'DELETE_USER'
                BEGIN
                  SELECT RAISE(ABORT, 'forced audit failure');
                END
                """);

        assertThrows(DataAccessException.class, () -> users.delete(targetId, "删除事务测试"));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class,
                targetId
        ));
        assertEquals(0, actionCount("DELETE_USER"));
    }

    @Test
    void createRollsBackMemberWhenAuditLogFails() {
        jdbc.execute("""
                CREATE TRIGGER fail_create_user_log
                BEFORE INSERT ON operation_logs
                WHEN NEW.action_type = 'CREATE_USER'
                BEGIN
                  SELECT RAISE(ABORT, 'forced audit failure');
                END
                """);

        assertThrows(DataAccessException.class, () -> users.create(new UserService.CreateUserRequest(
                "tx-create-target",
                "新增事务成员",
                "MEMBER",
                null,
                null,
                null,
                null
        )));

        assertEquals(0, userCount("tx-create-target"));
        assertEquals(0, actionCount("CREATE_USER"));
    }

    @Test
    void resetPasswordRollsBackHashWhenAuditLogFails() {
        long targetId = insertMember("tx-reset-target", "密码事务成员");
        String originalHash = passwordHash(targetId);
        jdbc.execute("""
                CREATE TRIGGER fail_reset_password_log
                BEFORE INSERT ON operation_logs
                WHEN NEW.action_type = 'RESET_PASSWORD'
                BEGIN
                  SELECT RAISE(ABORT, 'forced audit failure');
                END
                """);

        assertThrows(DataAccessException.class, () -> users.resetPassword(
                targetId,
                new UserService.ResetPasswordRequest("new-password", "密码事务测试")
        ));

        assertEquals(originalHash, passwordHash(targetId));
        assertEquals(0, actionCount("RESET_PASSWORD"));
    }

    @Test
    void importRollsBackMembersWhenAuditLogFails() throws Exception {
        jdbc.execute("""
                CREATE TRIGGER fail_import_users_log
                BEFORE INSERT ON operation_logs
                WHEN NEW.action_type = 'IMPORT_USERS'
                BEGIN
                  SELECT RAISE(ABORT, 'forced audit failure');
                END
                """);

        assertThrows(DataAccessException.class, () -> users.importMembers(memberImportFile(
                "tx-import-target",
                "导入事务成员"
        )));

        assertEquals(0, userCount("tx-import-target"));
        assertEquals(0, actionCount("IMPORT_USERS"));
    }

    @Test
    void onlyActiveAdminCannotDisableOwnAccount() {
        assertThrows(ApiException.class, () -> users.update(adminId, new UserService.UpdateUserRequest(
                "事务测试管理员",
                "ADMIN",
                "DISABLED",
                null,
                null,
                null,
                null,
                "禁止锁死系统测试"
        )));

        assertEquals("ACTIVE", status(adminId));
        assertEquals("ADMIN", role(adminId));
    }

    @Test
    void onlyActiveAdminCannotDemoteOwnAccount() {
        assertThrows(ApiException.class, () -> users.update(adminId, new UserService.UpdateUserRequest(
                "事务测试管理员",
                "PRESIDENT",
                "ACTIVE",
                null,
                null,
                null,
                null,
                "禁止锁死系统测试"
        )));

        assertEquals("ACTIVE", status(adminId));
        assertEquals("ADMIN", role(adminId));
    }

    @Test
    void adminCanDisableAnotherAdminWhenOneActiveAdminRemains() {
        long secondAdminId = insertAdmin("tx-second-admin", "第二管理员");

        users.update(secondAdminId, new UserService.UpdateUserRequest(
                "第二管理员",
                "ADMIN",
                "DISABLED",
                null,
                null,
                null,
                null,
                "管理员交接测试"
        ));

        assertEquals("DISABLED", status(secondAdminId));
        assertEquals("ACTIVE", status(adminId));
    }

    private long insertMember(String studentNo, String name) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES (?, ?, 'test-hash', 'MEMBER', 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name);
        if (id == null) {
            throw new IllegalStateException("测试成员创建失败");
        }
        return id;
    }

    private long insertAdmin(String studentNo, String name) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES (?, ?, 'test-hash', 'ADMIN', 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name);
        if (id == null) {
            throw new IllegalStateException("测试管理员创建失败");
        }
        return id;
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, id);
    }

    private String role(long id) {
        return jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, id);
    }

    private String passwordHash(long id) {
        return jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, id);
    }

    private int userCount(String studentNo) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE student_no = ?",
                Integer.class,
                studentNo
        );
        return count == null ? 0 : count;
    }

    private MockMultipartFile memberImportFile(String studentNo, String name) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("成员");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(studentNo);
            row.createCell(1).setCellValue(name);
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "members.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private int actionCount(String actionType) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = ?",
                Integer.class,
                actionType
        );
        return count == null ? 0 : count;
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("ca-user-transaction-test-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
