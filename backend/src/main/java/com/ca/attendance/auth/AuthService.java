package com.ca.attendance.auth;

import com.ca.attendance.access.RemoteAccessPolicy;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.TransactionCommitActions;
import com.ca.attendance.user.UserRepository;
import com.ca.attendance.user.UserInputPolicy;
import com.ca.attendance.user.UserSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private static final String MISSING_USER_PASSWORD_HASH =
            new BCryptPasswordEncoder().encode(UUID.randomUUID().toString());
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RemoteAccessPolicy remoteAccess;
    private final LoginAttemptGuard loginAttempts;
    private final AuthenticationEventService authenticationEvents;

    public AuthService(UserRepository users,
                       JdbcTemplate jdbc,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       RemoteAccessPolicy remoteAccess,
                       LoginAttemptGuard loginAttempts,
                       AuthenticationEventService authenticationEvents) {
        this.users = users;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.remoteAccess = remoteAccess;
        this.loginAttempts = loginAttempts;
        this.authenticationEvents = authenticationEvents;
    }

    public LoginResponse login(String studentNo, String password) {
        return login(studentNo, password, LoginContext.local());
    }

    public LoginResponse login(String studentNo, String password, LoginContext context) {
        loginAttempts.requireAllowed(studentNo, context);
        UserRepository.UserLoginRow user = null;
        try {
            Optional<UserRepository.UserLoginRow> candidate = users.findLoginByStudentNo(studentNo);
            String passwordHash = candidate.map(UserRepository.UserLoginRow::passwordHash)
                    .orElse(MISSING_USER_PASSWORD_HASH);
            boolean passwordMatches = passwordEncoder.matches(password, passwordHash);
            if (candidate.isEmpty() || !passwordMatches) {
                throw ApiException.unauthorized("学号或密码错误");
            }
            user = candidate.orElseThrow();
            if (!"ACTIVE".equals(user.status())) {
                throw ApiException.forbidden("账号已停用");
            }
            if (context.remote() && !remoteAccess.roleAllowed(user.role())) {
                throw ApiException.forbidden("远程后台仅允许会长或管理员登录");
            }
        } catch (ApiException ex) {
            LoginAttemptGuard.FailureResult failure = loginAttempts.recordFailure(studentNo, context);
            authenticationEvents.recordFailure(user, studentNo, context, ex.getMessage(), failure.lockedNow());
            throw ex;
        }
        authenticationEvents.recordSuccess(user, studentNo, context);
        loginAttempts.recordSuccess(studentNo, context);
        String token = tokenService.issue(user.id(), user.studentNo(), user.name(), user.role());
        return new LoginResponse(token, user.id(), user.studentNo(), user.name(), user.role().name(), user.mustChangePassword());
    }

    public UserSummary me() {
        return users.findSummaryById(AuthContext.current().id()).orElseThrow(() -> ApiException.unauthorized("账号不存在"));
    }

    @Transactional
    public void changePassword(String oldPassword, String newPassword) {
        AuthUser current = AuthContext.current();
        UserRepository.UserLoginRow user = users.findLoginByStudentNo(current.studentNo())
                .orElseThrow(() -> ApiException.unauthorized("账号不存在"));
        if (!passwordEncoder.matches(oldPassword, user.passwordHash())) {
            throw ApiException.badRequest("原密码错误");
        }
        String validatedPassword = UserInputPolicy.password(newPassword);
        int updated = jdbc.update(
                "UPDATE users SET password_hash = ?, must_change_password = 0, updated_by = ?, updated_at = datetime('now', 'localtime') WHERE id = ?",
                passwordEncoder.encode(validatedPassword),
                current.id(),
                current.id()
        );
        if (updated != 1) {
            throw ApiException.unauthorized("账号状态已经变化，请重新登录");
        }
        authenticationEvents.recordPasswordChange(user);
        TransactionCommitActions.runAfterCommit(() -> tokenService.revokeUser(current.id()));
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
