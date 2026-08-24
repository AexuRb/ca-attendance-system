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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        jdbc.execute("DROP TRIGGER IF EXISTS fail_update_profile_log");
        jdbc.update("DELETE FROM operation_logs");
        jdbc.update("DELETE FROM users WHERE student_no LIKE 'tx-%' OR student_no = '9900000001'");

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
        jdbc.execute("DROP TRIGGER IF EXISTS fail_update_profile_log");
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
                "9900000096",
                "新增事务成员",
                "MEMBER",
                null,
                null,
                null,
                null
        )));

        assertEquals(0, userCount("9900000096"));
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
                "9900000097",
                "导入事务成员"
        )));

        assertEquals(0, userCount("9900000097"));
        assertEquals(0, actionCount("IMPORT_USERS"));
    }

    @Test
    void presidentImportCannotModifyAdministratorProfile() throws Exception {
        long protectedAdminId = insertAdmin("9900000001", "受保护管理员");
        long presidentId = insertPresident("tx-president-import", "导入测试会长");
        AuthContext.set(new AuthUser(
                presidentId,
                "tx-president-import",
                "导入测试会长",
                Role.PRESIDENT,
                Instant.now().plusSeconds(3600)
        ));

        ApiException exception = assertThrows(ApiException.class, () -> users.importMembers(memberImportFile(
                "9900000001",
                "越权修改管理员"
        )));

        assertTrue(exception.getMessage().contains("管理员"));
        assertTrue(exception.getMessage().contains("未写入"));
        assertEquals("受保护管理员", name(protectedAdminId));
    }

    @Test
    void importRejectsWholeWorkbookWhenAnyMemberRowIsInvalid() throws Exception {
        MockMultipartFile file = memberImportFile(List.of(
                new String[]{"9900000098", "原子导入成员"},
                new String[]{"invalid-student-no", "无效成员"}
        ));

        ApiException exception = assertThrows(ApiException.class, () -> users.importMembers(file));

        assertTrue(exception.getMessage().contains("未写入"));
        assertEquals(0, userCount("9900000098"));
        assertEquals(0, actionCount("IMPORT_USERS"));
    }

    @Test
    void updateProfileRollsBackFieldsWhenAuditLogFails() {
        jdbc.update("UPDATE users SET phone = 'original-phone', major = '原学院', qq = '10000' WHERE id = ?", adminId);
        jdbc.execute("""
                CREATE TRIGGER fail_update_profile_log
                BEFORE INSERT ON operation_logs
                WHEN NEW.action_type = 'UPDATE_PROFILE'
                BEGIN
                  SELECT RAISE(ABORT, 'forced audit failure');
                END
                """);

        assertThrows(DataAccessException.class, () -> users.updateProfile(
                new UserService.ProfileRequest("new-phone", "新学院", null, "20000")
        ));

        var profile = jdbc.queryForMap("SELECT phone, major, qq FROM users WHERE id = ?", adminId);
        assertEquals("original-phone", profile.get("phone"));
        assertEquals("原学院", profile.get("major"));
        assertEquals("10000", profile.get("qq"));
        assertEquals(0, actionCount("UPDATE_PROFILE"));
    }

    @Test
    void createAndImportApplyTheSameNewAccountRule() throws Exception {
        ApiException createError = assertThrows(ApiException.class, () -> users.create(
                new UserService.CreateUserRequest(
                        "12345", "短学号成员", "MEMBER", null, null, null, null
                )
        ));
        ApiException importError = assertThrows(ApiException.class, () -> users.importMembers(
                memberImportFile("12345", "短学号成员")
        ));

        assertTrue(createError.getMessage().contains("6 至 32 位纯数字"));
        assertTrue(importError.getMessage().contains("第 2 行"));
        assertTrue(importError.getMessage().contains("6 至 32 位纯数字"));
        assertTrue(importError.getMessage().contains("未写入任何成员"));
        assertEquals(0, userCount("12345"));
    }

    @Test
    void importReportsOversizedNameByRowAndWritesNothing() throws Exception {
        MockMultipartFile file = memberImportFile(List.of(
                new String[]{"9900000088", "合法成员"},
                new String[]{"9900000089", "名".repeat(65)}
        ));

        ApiException exception = assertThrows(ApiException.class, () -> users.importMembers(file));

        assertTrue(exception.getMessage().contains("第 3 行"));
        assertTrue(exception.getMessage().contains("姓名不能超过 64"));
        assertEquals(0, userCount("9900000088"));
        assertEquals(0, userCount("9900000089"));
    }

    @Test
    void importRejectsFilesLargerThanFiveMegabytesBeforeParsing() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "members.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[5 * 1024 * 1024 + 1]
        );

        ApiException exception = assertThrows(ApiException.class, () -> users.importMembers(file));

        assertTrue(exception.getMessage().contains("不能超过 5 MB"));
        assertEquals(0, actionCount("IMPORT_USERS"));
    }

    @Test
    void importRejectsRowsBeyondTheThreeThousandRowLimit() throws Exception {
        MockMultipartFile file = memberImportFileAtRow(3001, "9900000095", "超限成员");

        ApiException exception = assertThrows(ApiException.class, () -> users.importMembers(file));

        assertTrue(exception.getMessage().contains("超过 3000 行"));
        assertEquals(0, userCount("9900000095"));
        assertEquals(0, actionCount("IMPORT_USERS"));
    }

    @Test
    void reimportWithBlankOptionalColumnsPreservesExistingProfile() throws Exception {
        long targetId = insertMember("9900000086", "已有资料成员");
        jdbc.update("""
                UPDATE users
                SET phone = '13800001234', major = '计算机学院', grade = '2024级', qq = '123456789'
                WHERE id = ?
                """, targetId);

        UserService.ImportResult result = users.importMembers(memberImportFile(
                "9900000086",
                "更新姓名成员"
        ));

        assertEquals(1, result.updated());
        assertEquals("更新姓名成员", profileValue(targetId, "name"));
        assertEquals("13800001234", profileValue(targetId, "phone"));
        assertEquals("计算机学院", profileValue(targetId, "major"));
        assertEquals("2024级", profileValue(targetId, "grade"));
        assertEquals("123456789", profileValue(targetId, "qq"));
    }

    @Test
    void importKeepsTwelveDigitNumericStudentNumberOutOfScientificNotation() throws Exception {
        MockMultipartFile file = memberImportFileWithNumericStudentNo(
                202301012345d,
                "长学号成员"
        );

        UserService.ImportResult result = users.importMembers(file);

        assertEquals(1, result.created());
        assertEquals(1, userCount("202301012345"));
    }

    @Test
    void importReadsStudentNumberFromFormulaResult() throws Exception {
        MockMultipartFile file = memberImportFileWithFormulaStudentNo(
                "202301012345+1",
                "公式学号成员"
        );

        UserService.ImportResult result = users.importMembers(file);

        assertEquals(1, result.created());
        assertEquals(1, userCount("202301012346"));
    }

    @Test
    void historicalAccountRequiresExplicitPasswordWhenReset() {
        long targetId = insertMember("legacy", "历史短账号");
        String before = passwordHash(targetId);

        ApiException exception = assertThrows(ApiException.class, () -> users.resetPassword(
                targetId,
                new UserService.ResetPasswordRequest(null, "默认重置测试")
        ));
        assertTrue(exception.getMessage().contains("手动输入"));
        assertEquals(before, passwordHash(targetId));

        users.resetPassword(
                targetId,
                new UserService.ResetPasswordRequest("manual-password", "手动重置测试")
        );
        assertTrue(!before.equals(passwordHash(targetId)));
    }

    @Test
    void profileAndMemberUpdatesRejectOversizedFields() {
        long targetId = insertMember("9900000087", "字段边界成员");

        ApiException memberError = assertThrows(ApiException.class, () -> users.update(
                targetId,
                new UserService.UpdateUserRequest(
                        "字段边界成员", "MEMBER", "ACTIVE", "1".repeat(65),
                        null, null, null, "字段边界测试"
                )
        ));
        ApiException profileError = assertThrows(ApiException.class, () -> users.updateProfile(
                new UserService.ProfileRequest(null, "院".repeat(129), null, null)
        ));

        assertTrue(memberError.getMessage().contains("联系方式不能超过 64"));
        assertTrue(profileError.getMessage().contains("学院不能超过 128"));
    }

    @Test
    void administratorImportCanModifyAdministratorProfile() throws Exception {
        long targetAdminId = insertAdmin("9900000001", "待更新管理员");

        UserService.ImportResult result = users.importMembers(memberImportFile(
                "9900000001",
                "已更新管理员"
        ));

        assertEquals(1, result.updated());
        assertEquals(0, result.skipped());
        assertEquals("已更新管理员", name(targetAdminId));
    }

    @Test
    void importCanUpdateAnExistingHistoricalAccountWithoutMigratingItsIdentifier() throws Exception {
        long targetId = insertMember("legacy-member", "历史成员");

        UserService.ImportResult result = users.importMembers(memberImportFile(
                "legacy-member",
                "历史成员新姓名"
        ));

        assertEquals(1, result.updated());
        assertEquals("历史成员新姓名", name(targetId));
        assertEquals(1, userCount("legacy-member"));
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

    private long insertPresident(String studentNo, String name) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES (?, ?, 'test-hash', 'PRESIDENT', 'ACTIVE', 0)
                RETURNING id
                """, Long.class, studentNo, name);
        if (id == null) {
            throw new IllegalStateException("测试会长创建失败");
        }
        return id;
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, id);
    }

    private String role(long id) {
        return jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, id);
    }

    private String name(long id) {
        return jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, id);
    }

    private String passwordHash(long id) {
        return jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, id);
    }

    private String profileValue(long id, String column) {
        if (!List.of("name", "phone", "major", "grade", "qq").contains(column)) {
            throw new IllegalArgumentException("不支持的测试字段");
        }
        return jdbc.queryForObject("SELECT " + column + " FROM users WHERE id = ?", String.class, id);
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
        return memberImportFile(List.<String[]>of(new String[]{studentNo, name}));
    }

    private MockMultipartFile memberImportFile(List<String[]> members) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("成员");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            for (int index = 0; index < members.size(); index++) {
                String[] member = members.get(index);
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(member[0]);
                row.createCell(1).setCellValue(member[1]);
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "members.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private MockMultipartFile memberImportFileAtRow(int rowIndex, String studentNo, String name) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("成员");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            Row row = sheet.createRow(rowIndex);
            row.createCell(0).setCellValue(studentNo);
            row.createCell(1).setCellValue(name);
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "members-row-limit.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private MockMultipartFile memberImportFileWithNumericStudentNo(double studentNo, String name) throws Exception {
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
                    "members-numeric-student-no.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private MockMultipartFile memberImportFileWithFormulaStudentNo(String formula, String name) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("成员");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellFormula(formula);
            row.createCell(1).setCellValue(name);
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "members-formula-student-no.xlsx",
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
