package com.ca.attendance.auth;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthTransactionIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();

    @Autowired
    private AuthService auth;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private TokenService tokens;

    private long userId;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_change_password_log");
        jdbc.update("DELETE FROM operation_logs");
        jdbc.update("DELETE FROM users WHERE student_no = 'auth-tx-admin'");
        userId = requiredId(jdbc.queryForObject("""
                INSERT INTO users (
                  student_no, name, password_hash, role, status, must_change_password
                ) VALUES ('auth-tx-admin', '认证事务管理员', ?, 'ADMIN', 'ACTIVE', 1)
                RETURNING id
                """, Long.class, passwords.encode("old-password")));
        AuthContext.set(new AuthUser(
                userId,
                "auth-tx-admin",
                "认证事务管理员",
                Role.ADMIN,
                Instant.now().plusSeconds(3600)
        ));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        jdbc.execute("DROP TRIGGER IF EXISTS fail_change_password_log");
    }

    @Test
    void changePasswordRollsBackAndPreservesSessionsWhenAuditFails() {
        String originalHash = passwordHash();
        String token = tokens.issue(userId, "auth-tx-admin", "认证事务管理员", Role.ADMIN);
        jdbc.execute("""
                CREATE TRIGGER fail_change_password_log
                BEFORE INSERT ON operation_logs
                WHEN NEW.action_type = 'CHANGE_PASSWORD'
                BEGIN
                  SELECT RAISE(ABORT, 'forced password audit failure');
                END
                """);

        assertThrows(DataAccessException.class,
                () -> auth.changePassword("old-password", "new-password"));

        assertEquals(originalHash, passwordHash());
        assertEquals(0, actionCount("CHANGE_PASSWORD"));
        assertDoesNotThrow(() -> tokens.require(token));
    }

    @Test
    void changePasswordAuditsAndRevokesSessionsAfterCommit() {
        String token = tokens.issue(userId, "auth-tx-admin", "认证事务管理员", Role.ADMIN);

        auth.changePassword("old-password", "new-password");

        assertTrue(passwords.matches("new-password", passwordHash()));
        assertEquals(1, actionCount("CHANGE_PASSWORD"));
        assertThrows(ApiException.class, () -> tokens.require(token));
    }

    private String passwordHash() {
        return jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?",
                String.class,
                userId
        );
    }

    private int actionCount(String actionType) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE action_type = ?",
                Integer.class,
                actionType
        );
        return count == null ? 0 : count;
    }

    private static long requiredId(Long id) {
        if (id == null) {
            throw new IllegalStateException("测试用户创建失败");
        }
        return id;
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("attendance-auth-transaction-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
