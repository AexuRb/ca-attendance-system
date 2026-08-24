package com.ca.attendance.setup;

import com.ca.attendance.access.RemoteAccessPolicy;
import com.ca.attendance.auth.AuthService;
import com.ca.attendance.auth.AuthenticationEventService;
import com.ca.attendance.auth.LoginAttemptGuard;
import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.config.DatabaseMigrator;
import com.ca.attendance.config.SQLiteDataSourceConfiguration;
import com.ca.attendance.config.StoragePaths;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.user.UserRepository;
import com.zaxxer.hikari.HikariDataSource;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupServiceIntegrationTest {
    @TempDir
    Path tempDirectory;

    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private SetupService setup;

    @BeforeEach
    void setUp() throws Exception {
        StoragePaths paths = new StoragePaths(tempDirectory.toString());
        dataSource = (HikariDataSource) new SQLiteDataSourceConfiguration().dataSource(paths);
        new DatabaseMigrator(dataSource).run();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM operation_logs");
        jdbc.update("DELETE FROM users");

        PasswordEncoder passwords = new BCryptPasswordEncoder();
        TokenService tokens = new TokenService(12);
        AuthService auth = new AuthService(
                new UserRepository(jdbc), jdbc, passwords, tokens,
                new RemoteAccessPolicy(8081), new LoginAttemptGuard(),
                new AuthenticationEventService(
                        jdbc,
                        new OperationLogService(jdbc, new ObjectMapper())
                )
        );
        setup = new SetupService(
                jdbc,
                passwords,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                auth
        );
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void initializesOnlyAnEmptyDatabaseAndLogsInAdministrator() {
        assertFalse(setup.status().initialized());

        AuthService.LoginResponse login = setup.initialize(
                new SetupService.SetupRequest("9900000001", "首位管理员", "12345678")
        );

        assertNotNull(login.token());
        assertEquals("ADMIN", login.role());
        assertFalse(login.mustChangePassword());
        assertTrue(setup.status().initialized());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = 'INITIALIZE_SYSTEM'",
                Integer.class
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = 'LOCAL_LOGIN_SUCCESS'",
                Integer.class
        ));

        assertThrows(ApiException.class, () -> setup.initialize(
                new SetupService.SetupRequest("9900000002", "第二位管理员", "12345678")
        ));
    }

    @Test
    void rejectsInvalidAdministratorInputBeforeWritingAnything() {
        ApiException accountError = assertThrows(ApiException.class, () -> setup.initialize(
                new SetupService.SetupRequest("admin", "管理员", "12345678")
        ));
        ApiException nameError = assertThrows(ApiException.class, () -> setup.initialize(
                new SetupService.SetupRequest("9900000001", " ", "12345678")
        ));
        ApiException passwordError = assertThrows(ApiException.class, () -> setup.initialize(
                new SetupService.SetupRequest("9900000001", "管理员", "12345")
        ));

        assertTrue(accountError.getMessage().contains("纯数字"));
        assertTrue(nameError.getMessage().contains("姓名不能为空"));
        assertTrue(passwordError.getMessage().contains("6 至 64"));
        assertFalse(setup.status().initialized());
    }

    @Test
    void configuredInitializationCreatesAnAuditedAdministratorOnlyInAnEmptyDatabase() {
        assertTrue(setup.initializeConfigured("9900000001", "12345678"));

        assertEquals("ADMIN", jdbc.queryForObject(
                "SELECT role FROM users WHERE student_no = ?",
                String.class,
                "9900000001"
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT must_change_password FROM users WHERE student_no = ?",
                Integer.class,
                "9900000001"
        ));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = 'INITIALIZE_SYSTEM'",
                Integer.class
        ));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = 'LOCAL_LOGIN_SUCCESS'",
                Integer.class
        ));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM operation_logs
                WHERE COALESCE(before_data, '') LIKE '%12345678%'
                   OR COALESCE(after_data, '') LIKE '%12345678%'
                   OR COALESCE(reason, '') LIKE '%12345678%'
                """, Integer.class));
    }

    @Test
    void configuredInitializationNeverPromotesAnExistingMember() {
        jdbc.update("""
                INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                VALUES ('9900000001', '已有成员', 'original-hash', 'MEMBER', 'ACTIVE', 0)
                """);

        assertFalse(setup.initializeConfigured("9900000001", "12345678"));

        assertEquals("MEMBER", jdbc.queryForObject(
                "SELECT role FROM users WHERE student_no = '9900000001'",
                String.class
        ));
        assertEquals("original-hash", jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE student_no = '9900000001'",
                String.class
        ));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = 'INITIALIZE_SYSTEM'",
                Integer.class
        ));
    }
}
