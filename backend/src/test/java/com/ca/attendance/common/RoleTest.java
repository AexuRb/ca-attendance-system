package com.ca.attendance.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {
    @Test
    void ministerCanExportStatisticsWithoutReceivingSensitiveManagementPermissions() {
        assertThat(Role.MINISTER.canExport()).isTrue();
        assertThat(Role.MINISTER.canManageUsers()).isFalse();
        assertThat(Role.MINISTER.canSetDutyWeekdays()).isFalse();
        assertThat(Role.MINISTER.canViewOperationLogs()).isFalse();
    }
}
