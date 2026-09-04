package com.ca.attendance.auth;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AuthenticationEventService {
    private final JdbcTemplate jdbc;
    private final OperationLogService logs;

    public AuthenticationEventService(JdbcTemplate jdbc, OperationLogService logs) {
        this.jdbc = jdbc;
        this.logs = logs;
    }

    @Transactional
    public void recordSuccess(UserRepository.UserLoginRow user,
                              String attemptedAccount,
                              AuthService.LoginContext context) {
        int updated = jdbc.update(
                "UPDATE users SET last_login_at = ? WHERE id = ?",
                LocalDateTime.now(),
                user.id()
        );
        if (updated != 1) {
            throw ApiException.unauthorized("账号状态已经变化，请重新登录");
        }
        logs.logAuthentication(
                OperationLogService.AuthenticationOutcome.SUCCESS,
                context.remote(),
                user,
                attemptedAccount,
                context.clientAddress(),
                context.userAgent(),
                context.remote() ? "远程后台登录成功" : "本机后台登录成功"
        );
    }

    @Transactional
    public void recordFailure(UserRepository.UserLoginRow user,
                              String attemptedAccount,
                              AuthService.LoginContext context,
                              String reason,
                              boolean lockedNow) {
        logs.logAuthentication(
                lockedNow
                        ? OperationLogService.AuthenticationOutcome.LOCKED
                        : OperationLogService.AuthenticationOutcome.FAILURE,
                context.remote(),
                user,
                attemptedAccount,
                context.clientAddress(),
                context.userAgent(),
                reason
        );
    }

    @Transactional
    public void recordPasswordChange(UserRepository.UserLoginRow user) {
        logs.log(
                "CHANGE_PASSWORD",
                "users",
                user.id(),
                Map.of("mustChangePassword", user.mustChangePassword()),
                Map.of("mustChangePassword", false),
                "修改登录密码"
        );
    }
}
