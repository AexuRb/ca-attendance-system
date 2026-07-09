package com.ca.attendance.log;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.Role;
import com.ca.attendance.maintenance.BackupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationLogQueryServiceTest {
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private BackupService backups;

    @BeforeEach
    void setAuthUser() {
        AuthContext.set(new AuthUser(1L, "admin", "管理员", Role.ADMIN, Instant.now().plusSeconds(3600)));
    }

    @AfterEach
    void clearAuthUser() {
        AuthContext.clear();
    }

    @Test
    void clearCreatesSafetyBackupBeforeDeletingLogs() {
        OperationLogQueryService service = new OperationLogQueryService(jdbc, backups);
        BackupService.BackupItem backup = new BackupService.BackupItem("backup_logs.zip", 100L, Instant.now());

        when(backups.create()).thenReturn(backup);
        when(jdbc.update("DELETE FROM operation_logs")).thenReturn(12);

        OperationLogQueryService.ClearResult result = service.clear();

        assertThat(result.deleted()).isEqualTo(12);
        assertThat(result.safetyBackup()).isEqualTo(backup);
        verify(backups).create();
        verify(jdbc).update("DELETE FROM operation_logs");
    }
}
