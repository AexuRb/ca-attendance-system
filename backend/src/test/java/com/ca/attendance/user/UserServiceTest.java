package com.ca.attendance.user;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.maintenance.BackupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository users;
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OperationLogService logs;
    @Mock
    private BackupService backups;
    @Mock
    private TokenService tokens;

    @BeforeEach
    void setAuthUser() {
        AuthContext.set(new AuthUser(1L, "admin", "管理员", Role.ADMIN, Instant.now().plusSeconds(3600)));
    }

    @AfterEach
    void clearAuthUser() {
        AuthContext.clear();
    }

    @Test
    void bulkDisableCreatesSafetyBackupBeforeChangingUsers() {
        UserService service = new UserService(users, jdbc, passwordEncoder, logs, backups, tokens);
        BackupService.BackupItem backup = new BackupService.BackupItem("backup_test.zip", 100L, Instant.now());
        UserSummary target = user(2L, "20230002", "李四", Role.MEMBER, "ACTIVE");

        when(users.findSummaryById(2L)).thenReturn(Optional.of(target));
        when(backups.create()).thenReturn(backup);

        UserService.BulkStatusResult result = service.bulkStatus(new UserService.BulkStatusRequest(
                List.of(2L), null, null, null, null, "DISABLED", "批量停用测试"
        ));

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.safetyBackup()).isEqualTo(backup);
        verify(backups).create();
        verify(jdbc).update(contains("UPDATE users"), eq("DISABLED"), eq("DISABLED"), eq("DISABLED"),
                eq(1L), eq(1L), eq(2L));
        verify(tokens).revokeUser(2L);
    }

    private UserSummary user(long id, String studentNo, String name, Role role, String status) {
        LocalDateTime now = LocalDateTime.now();
        return new UserSummary(id, studentNo, name, role, status, null, null, null, null, false, now, now);
    }
}
