package com.ca.attendance.auth;

import com.ca.attendance.access.RemoteAccessPolicy;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.user.UserRepository;
import com.ca.attendance.user.UserSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RemoteAccessPolicy remoteAccess;
    private final RemoteLoginAttemptGuard remoteLoginAttempts;
    private final OperationLogService logs;

    public AuthService(UserRepository users, JdbcTemplate jdbc, PasswordEncoder passwordEncoder, TokenService tokenService,
                       RemoteAccessPolicy remoteAccess, RemoteLoginAttemptGuard remoteLoginAttempts,
                       OperationLogService logs) {
        this.users = users;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.remoteAccess = remoteAccess;
        this.remoteLoginAttempts = remoteLoginAttempts;
        this.logs = logs;
    }

    public LoginResponse login(String studentNo, String password) {
        return login(studentNo, password, LoginContext.local());
    }

    public LoginResponse login(String studentNo, String password, LoginContext context) {
        if (context.remote()) {
            remoteLoginAttempts.requireAllowed(studentNo, context);
        }
        UserRepository.UserLoginRow user = null;
        try {
            user = users.findLoginByStudentNo(studentNo)
                    .orElseThrow(() -> ApiException.unauthorized("学号或密码错误"));
            if (!"ACTIVE".equals(user.status())) {
                throw ApiException.forbidden("账号已停用");
            }
            if (!passwordEncoder.matches(password, user.passwordHash())) {
                throw ApiException.unauthorized("学号或密码错误");
            }
            if (context.remote() && !remoteAccess.roleAllowed(user.role())) {
                throw ApiException.forbidden("远程后台仅允许会长或管理员登录");
            }
        } catch (ApiException ex) {
            if (context.remote()) {
                remoteLoginAttempts.recordFailure(studentNo, context);
                logs.logRemoteAuthentication(false, user, studentNo, context.clientAddress(), context.userAgent(), ex.getMessage());
            }
            throw ex;
        }
        jdbc.update("UPDATE users SET last_login_at = ? WHERE id = ?", LocalDateTime.now(), user.id());
        String token = tokenService.issue(user.id(), user.studentNo(), user.name(), user.role());
        if (context.remote()) {
            remoteLoginAttempts.recordSuccess(studentNo, context);
            logs.logRemoteAuthentication(true, user, studentNo, context.clientAddress(), context.userAgent(), "远程后台登录成功");
        }
        return new LoginResponse(token, user.id(), user.studentNo(), user.name(), user.role().name(), user.mustChangePassword());
    }

    public UserSummary me() {
        return users.findSummaryById(AuthContext.current().id()).orElseThrow(() -> ApiException.unauthorized("账号不存在"));
    }

    public void changePassword(String oldPassword, String newPassword) {
        AuthUser current = AuthContext.current();
        UserRepository.UserLoginRow user = users.findLoginByStudentNo(current.studentNo())
                .orElseThrow(() -> ApiException.unauthorized("账号不存在"));
        if (!passwordEncoder.matches(oldPassword, user.passwordHash())) {
            throw ApiException.badRequest("原密码错误");
        }
        jdbc.update("UPDATE users SET password_hash = ?, must_change_password = 0, updated_by = ?, updated_at = datetime('now', 'localtime') WHERE id = ?",
                passwordEncoder.encode(newPassword), current.id(), current.id());
        tokenService.revokeUser(current.id());
    }

    public void logout(String token) {
        tokenService.revoke(token);
    }

    public record LoginResponse(String token, long id, String studentNo, String name, String role, boolean mustChangePassword) {
    }

    public record LoginContext(boolean remote, String clientAddress, String userAgent) {
        public static LoginContext local() {
            return new LoginContext(false, "127.0.0.1", "local");
        }
    }
}
