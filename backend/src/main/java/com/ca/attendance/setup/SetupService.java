package com.ca.attendance.setup;

import com.ca.attendance.auth.AuthService;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.user.UserInputPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SetupService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactions;
    private final AuthService authService;

    public SetupService(JdbcTemplate jdbc,
                        PasswordEncoder passwordEncoder,
                        TransactionTemplate transactions,
                        AuthService authService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.transactions = transactions;
        this.authService = authService;
    }

    public SetupStatus status() {
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        return new SetupStatus(users != null && users > 0);
    }

    public AuthService.LoginResponse initialize(SetupRequest request) {
        String account = UserInputPolicy.newStudentNo(request.account());
        String name = UserInputPolicy.name(request.name());
        String password = UserInputPolicy.password(request.password());

        transactions.executeWithoutResult(status -> {
            Integer userCount = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            if (userCount != null && userCount > 0) {
                throw ApiException.badRequest("系统已经完成初始化");
            }
            createAdministrator(account, name, password, false);
        });

        return authService.login(account, password);
    }

    public boolean initializeConfigured(String configuredAccount, String configuredPassword) {
        if (configuredPassword == null || configuredPassword.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(transactions.execute(status -> {
            Integer userCount = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            if (userCount != null && userCount > 0) {
                return false;
            }
            String account = UserInputPolicy.newStudentNo(configuredAccount);
            String password = UserInputPolicy.password(configuredPassword);
            createAdministrator(account, "初始管理员", password, true);
            return true;
        }));
    }

    private void createAdministrator(String account, String name, String password, boolean mustChangePassword) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (
                  student_no, name, password_hash, role, status, must_change_password
                )
                VALUES (?, ?, ?, 'ADMIN', 'ACTIVE', ?)
                RETURNING id
                """, Long.class, account, name, passwordEncoder.encode(password), mustChangePassword ? 1 : 0);
        if (id == null) {
            throw ApiException.badRequest("管理员创建失败");
        }
        int ownershipUpdated = jdbc.update(
                "UPDATE users SET created_by = ?, updated_by = ? WHERE id = ?",
                id,
                id,
                id
        );
        if (ownershipUpdated != 1) {
            throw ApiException.badRequest("管理员初始化失败");
        }
        int auditInserted = jdbc.update("""
                INSERT INTO operation_logs (
                  operator_user_id, operator_student_no, operator_name, action_type,
                  target_type, target_id, after_data, reason
                )
                VALUES (?, ?, ?, 'INITIALIZE_SYSTEM', 'users', ?, ?, '首次启动创建管理员')
                """, id, account, name, id, "{\"role\":\"ADMIN\"}");
        if (auditInserted != 1) {
            throw new IllegalStateException("系统初始化审计写入失败");
        }
    }

    public record SetupStatus(boolean initialized) {
    }

    public record SetupRequest(String account, String name, String password) {
    }
}
