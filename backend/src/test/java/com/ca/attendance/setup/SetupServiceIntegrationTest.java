package com.ca.attendance.setup;

import com.ca.attendance.auth.AuthService;
import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.config.DatabaseMigrator;
import com.ca.attendance.config.SQLiteDataSourceConfiguration;
import com.ca.attendance.config.StoragePaths;
import com.ca.attendance.user.UserRepository;
import com.zaxxer.hikari.HikariDataSource;
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

        PasswordEncoder passwords = new BCryptPasswordEncoder();
        TokenService tokens = new TokenService(12);
        AuthService auth = new AuthService(new UserRepository(jdbc), jdbc, passwords, tokens);
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
        assertEquals(0, setup.status().userCount());

        AuthService.LoginResponse login = setup.initialize(
                new SetupService.SetupRequest("admin_2026", "首位管理员", "12345678")
        );

        assertNotNull(login.token());
        assertEquals("ADMIN", login.role());
        assertFalse(login.mustChangePassword());
        assertTrue(setup.status().initialized());
        assertEquals(1, setup.status().userCount());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = 'INITIALIZE_SYSTEM'",
                Integer.class
        ));

        assertThrows(ApiException.class, () -> setup.initialize(
                new SetupService.SetupRequest("second_admin", "第二位管理员", "12345678")
        ));
    }
}
