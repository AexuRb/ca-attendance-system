package com.ca.attendance.setup;

import com.ca.attendance.auth.AuthService;
import com.ca.attendance.common.ApiException;
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
        Integer administrators = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE'",
                Integer.class
        );
        return new SetupStatus(
                administrators != null && administrators > 0,
                users == null ? 0 : users
        );
    }

    public AuthService.LoginResponse initialize(SetupRequest request) {
        String account = request.account().trim();
        String name = request.name().trim();
        String password = request.password();

        transactions.executeWithoutResult(status -> {
            Integer userCount = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            if (userCount != null && userCount > 0) {
                throw ApiException.badRequest("系统已经完成初始化");
            }

            Long id = jdbc.queryForObject("""
                    INSERT INTO users (
                      student_no, name, password_hash, role, status, must_change_password
                    )
                    VALUES (?, ?, ?, 'ADMIN', 'ACTIVE', 0)
                    RETURNING id
                    """, Long.class, account, name, passwordEncoder.encode(password));
            if (id == null) {
                throw ApiException.badRequest("管理员创建失败");
            }
            jdbc.update("UPDATE users SET created_by = ?, updated_by = ? WHERE id = ?", id, id, id);
            jdbc.update("""
                    INSERT INTO operation_logs (
                      operator_user_id, operator_student_no, operator_name, action_type,
                      target_type, target_id, after_data, reason
                    )
                    VALUES (?, ?, ?, 'INITIALIZE_SYSTEM', 'users', ?, ?, '首次启动创建管理员')
                    """, id, account, name, id, "{\"role\":\"ADMIN\"}");
        });

        return authService.login(account, password);
    }

    public record SetupStatus(boolean initialized, int userCount) {
    }

    public record SetupRequest(String account, String name, String password) {
    }
}
