package com.ca.attendance.access;

import com.ca.attendance.common.Role;
import org.junit.jupiter.api.Test;

import static com.ca.attendance.access.RolePermissionPolicy.Permission.*;
import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionPolicyTest {
    @Test
    void ministerOnlyReceivesDelegatedOperationalPermissions() {
        assertThat(RolePermissionPolicy.allows(Role.MINISTER, ATTENDANCE_MANAGE)).isTrue();
        assertThat(RolePermissionPolicy.allows(Role.MINISTER, REPAIR_MANAGE)).isTrue();
        assertThat(RolePermissionPolicy.allows(Role.MINISTER, STATS_EXPORT)).isTrue();
        assertThat(RolePermissionPolicy.allows(Role.MINISTER, MEMBERS_MANAGE)).isFalse();
        assertThat(RolePermissionPolicy.allows(Role.MINISTER, SCHEDULE_MANAGE)).isFalse();
        assertThat(RolePermissionPolicy.allows(Role.MINISTER, TRAINING_MANAGE)).isFalse();
    }

    @Test
    void presidentCannotReceiveAdministratorOnlyPermissions() {
        assertThat(RolePermissionPolicy.allows(Role.PRESIDENT, MEMBERS_MANAGE)).isTrue();
        assertThat(RolePermissionPolicy.allows(Role.PRESIDENT, BACKUPS)).isTrue();
        assertThat(RolePermissionPolicy.allows(Role.PRESIDENT, MEMBERS_DELETE)).isFalse();
        assertThat(RolePermissionPolicy.allows(Role.PRESIDENT, OPERATION_LOGS)).isFalse();
        assertThat(RolePermissionPolicy.allows(Role.PRESIDENT, BACKUP_ADMIN)).isFalse();
    }

    @Test
    void administratorReceivesEveryDeclaredPermission() {
        for (RolePermissionPolicy.Permission permission : RolePermissionPolicy.Permission.values()) {
            assertThat(RolePermissionPolicy.allows(Role.ADMIN, permission))
                    .as(permission.name())
                    .isTrue();
        }
    }
}
