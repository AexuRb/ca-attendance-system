package com.ca.attendance.desktop;

import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.maintenance.BackupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Service
public class DesktopControlService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final BackupService backups;
    private final ConfigurableApplicationContext applicationContext;
    private final String recoveryToken;

    public DesktopControlService(JdbcTemplate jdbc,
                                 PasswordEncoder passwordEncoder,
                                 TokenService tokens,
                                 BackupService backups,
                                 ConfigurableApplicationContext applicationContext,
                                 @Value("${app.desktop.control-token:${APP_RECOVERY_TOKEN:}}") String recoveryToken) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.backups = backups;
        this.applicationContext = applicationContext;
        this.recoveryToken = recoveryToken == null ? "" : recoveryToken;
    }

    public RecoveryResult resetAdministrator(String suppliedToken, String account, String newPassword) {
        requireToken(suppliedToken);
        List<AdminRow> matches = jdbc.query("""
                SELECT id, student_no, name
                FROM users
                WHERE student_no = ? AND role = 'ADMIN' AND status = 'ACTIVE'
                LIMIT 1
                """, (result, rowNumber) -> new AdminRow(
                result.getLong("id"),
                result.getString("student_no"),
                result.getString("name")
        ), account.trim());
        if (matches.isEmpty()) {
            throw ApiException.notFound("未找到可恢复的管理员账号");
        }

        AdminRow admin = matches.get(0);
        BackupService.BackupItem safetyBackup = backups.createSystemBackup("重置管理员密码前自动备份");
        jdbc.update("""
                UPDATE users
                SET password_hash = ?, must_change_password = 0, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, passwordEncoder.encode(newPassword), admin.id(), admin.id());
        jdbc.update("""
                INSERT INTO operation_logs (
                  operator_user_id, operator_student_no, operator_name, action_type,
                  target_type, target_id, reason
                )
                VALUES (NULL, 'LOCAL_SYSTEM', '本机恢复工具', 'RECOVER_ADMIN_PASSWORD',
                        'users', ?, ?)
                """, admin.id(), "本机重置管理员密码，安全备份：" + safetyBackup.filename());
        tokens.revokeAll();
        return new RecoveryResult(admin.studentNo(), admin.name(), safetyBackup.filename());
    }

    public void shutdown(String suppliedToken) {
        requireToken(suppliedToken);
        Thread shutdown = new Thread(() -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            applicationContext.close();
        }, "desktop-shutdown");
        shutdown.setDaemon(false);
        shutdown.start();
    }

    private void requireToken(String suppliedToken) {
        if (recoveryToken.isBlank() || suppliedToken == null || suppliedToken.isBlank()) {
            throw ApiException.forbidden("桌面控制通道未启用");
        }
        byte[] expected = recoveryToken.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = suppliedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw ApiException.forbidden("桌面控制令牌无效");
        }
    }

    private record AdminRow(long id, String studentNo, String name) {
    }

    public record RecoveryResult(String account, String name, String safetyBackup) {
    }
}
