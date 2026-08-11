package com.ca.attendance.access;

import com.ca.attendance.common.Role;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static com.ca.attendance.access.RolePermissionPolicy.Permission.*;
import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionPolicyTest {
    @Test
    void memberReceivesNoManagementPermission() {
        assertAllowed(Role.MEMBER, Set.of());
    }

    @Test
    void ministerOnlyReceivesDelegatedOperationalPermissions() {
        assertAllowed(Role.MINISTER, EnumSet.of(
                ATTENDANCE_MANAGE,
                REPAIR_MANAGE,
                STATS_VIEW,
                STATS_EXPORT
        ));
    }

    @Test
    void presidentReceivesAssociationManagementWithoutAdministratorOperations() {
        assertAllowed(Role.PRESIDENT, EnumSet.of(
                ATTENDANCE_MANAGE,
                ATTENDANCE_CREATE,
                MEMBERS_MANAGE,
                SCHEDULE_MANAGE,
                DUTY_SETTINGS_MANAGE,
                TRAINING_MANAGE,
                REPAIR_MANAGE,
                REPAIR_EXPORT,
                REPAIR_DELETE,
                STATS_VIEW,
                STATS_EXPORT,
                CUSTOM_EXPORT,
                BACKUPS,
                DATA_CENTER,
                REMOTE_ADMIN
        ));
    }

    @Test
    void administratorReceivesEveryDeclaredPermission() {
        assertAllowed(Role.ADMIN, EnumSet.allOf(RolePermissionPolicy.Permission.class));
    }

    private void assertAllowed(Role role, Set<RolePermissionPolicy.Permission> expected) {
        for (RolePermissionPolicy.Permission permission : RolePermissionPolicy.Permission.values()) {
            assertThat(RolePermissionPolicy.allows(role, permission))
                    .as(role.name() + " / " + permission.name())
                    .isEqualTo(expected.contains(permission));
        }
    }
}
