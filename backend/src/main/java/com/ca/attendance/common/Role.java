package com.ca.attendance.common;

public enum Role {
    MEMBER,
    MINISTER,
    PRESIDENT,
    ADMIN;

    public boolean atLeastManager() {
        return this == MINISTER || this == PRESIDENT || this == ADMIN;
    }

    public boolean canManageUsers() {
        return this == PRESIDENT || this == ADMIN;
    }

    public boolean canExport() {
        return this == PRESIDENT || this == ADMIN;
    }

    public boolean canSetDutyWeekdays() {
        return this == PRESIDENT || this == ADMIN;
    }

    public boolean canViewOperationLogs() {
        return this == PRESIDENT || this == ADMIN;
    }
}
