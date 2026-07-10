package com.ca.attendance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class InitialAdminInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final String studentNo;
    private final String password;

    public InitialAdminInitializer(JdbcTemplate jdbc,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${app.initial-admin.student-no}") String studentNo,
                                   @Value("${app.initial-admin.password:}") String password) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.studentNo = studentNo;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        Integer adminCount = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", Integer.class);
        if (adminCount != null && adminCount > 0) {
            return;
        }
        if (password == null || password.isBlank()) {
            return;
        }
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE student_no = ?", Integer.class, studentNo);
        if (exists != null && exists > 0) {
            jdbc.update("""
                    UPDATE users
                    SET role = 'ADMIN', password_hash = ?, status = 'ACTIVE', must_change_password = 1, updated_at = datetime('now', 'localtime')
                    WHERE student_no = ?
                    """, passwordEncoder.encode(password), studentNo);
        } else {
            jdbc.update("""
                    INSERT INTO users (student_no, name, password_hash, role, status, must_change_password)
                    VALUES (?, '初始管理员', ?, 'ADMIN', 'ACTIVE', 1)
                    """, studentNo, passwordEncoder.encode(password));
        }
    }
}
