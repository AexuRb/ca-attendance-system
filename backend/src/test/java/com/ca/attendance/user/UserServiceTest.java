package com.ca.attendance.user;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.Role;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.maintenance.BackupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private UserDeletionHistoryGuard deletionHistory;

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
        UserService service = new UserService(users, jdbc, passwordEncoder, logs, backups, tokens, deletionHistory);
        BackupService.BackupItem backup = new BackupService.BackupItem("backup_test.zip", 100L, Instant.now());
        UserSummary target = user(2L, "20230002", "李四", Role.MEMBER, "ACTIVE");

        when(users.findSummaryById(2L)).thenReturn(Optional.of(target));
        when(backups.create()).thenReturn(backup);
        when(jdbc.batchUpdate(anyString(), anyList())).thenReturn(new int[]{1});

        UserService.BulkStatusResult result = service.bulkStatus(new UserService.BulkStatusRequest(
                List.of(2L), null, null, null, null, "DISABLED", "批量停用测试"
        ));

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.safetyBackup()).isEqualTo(backup);
        verify(backups).create();
        verify(jdbc).batchUpdate(contains("UPDATE users"), anyList());
        verify(tokens).revokeUser(2L);
    }

    @Test
    void presidentCannotAppointAdministrator() {
        UserService service = new UserService(users, jdbc, passwordEncoder, logs, backups, tokens, deletionHistory);
        AuthContext.set(new AuthUser(3L, "president", "测试会长", Role.PRESIDENT, Instant.now().plusSeconds(3600)));

        assertThatThrownBy(() -> service.create(new UserService.CreateUserRequest(
                "new-admin", "新管理员", "ADMIN", null, null, null, null
        ))).isInstanceOf(ApiException.class).hasMessageContaining("管理员只能由管理员任命");
    }

    @Test
    void changingOneAssessmentPreservesTheOtherAndAuditsTheChange() {
        UserService service = new UserService(users, jdbc, passwordEncoder, logs, backups, tokens, deletionHistory);
        LocalDateTime now = LocalDateTime.now();
        UserSummary before = new UserSummary(2L, "9900000002", "示例成员", Role.MEMBER, "ACTIVE",
                null, null, null, null, false, now, now, AssessmentStatus.NOT_PASSED, AssessmentStatus.PASSED);
        UserSummary after = new UserSummary(2L, "9900000002", "示例成员", Role.MEMBER, "ACTIVE",
                null, null, null, null, false, now, now, AssessmentStatus.PASSED, AssessmentStatus.PASSED);
        when(users.findSummaryById(2L)).thenReturn(Optional.of(before), Optional.of(after));
        when(jdbc.update(contains("role = 'MEMBER'"), eq("PASSED"), eq("PASSED"), eq(1L), eq(2L),
                eq("NOT_PASSED"), eq("PASSED"))).thenReturn(1);

        assertThat(service.updateAssessments(2L, new UserService.AssessmentUpdateRequest("PASSED", null, "考核通过")))
                .isEqualTo(after);
        verify(logs).log("UPDATE_USER", "users", 2L, before, after, "考核通过");
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "PRESIDENT", "MINISTER"})
    void evenAnAdministratorCannotChangeOfficerAssessments(Role role) {
        UserService service = new UserService(users, jdbc, passwordEncoder, logs, backups, tokens, deletionHistory);
        when(users.findSummaryById(2L)).thenReturn(Optional.of(user(2L, "9900000002", "示例成员", role, "ACTIVE")));
        assertThatThrownBy(() -> service.updateAssessments(2L,
                new UserService.AssessmentUpdateRequest(null, "NOT_PASSED", null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("固定为通过");
        verifyNoInteractions(jdbc, logs);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"MINISTER", "MEMBER"})
    void nonManagersCannotMaintainAssessments(Role role) {
        UserService service = new UserService(users, jdbc, passwordEncoder, logs, backups, tokens, deletionHistory);
        AuthContext.set(new AuthUser(3L, "operator", "示例操作人", role, Instant.now().plusSeconds(3600)));
        assertThatThrownBy(() -> service.updateAssessments(2L,
                new UserService.AssessmentUpdateRequest("PASSED", null, null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("无权管理成员");
        verifyNoInteractions(users, jdbc, logs);
    }

    private UserSummary user(long id, String studentNo, String name, Role role, String status) {
        LocalDateTime now = LocalDateTime.now();
        AssessmentStatus assessment = role == Role.MEMBER ? AssessmentStatus.NOT_PASSED : AssessmentStatus.PASSED;
        return new UserSummary(id, studentNo, name, role, status, null, null, null, null, false, now, now, assessment, assessment);
    }
}
