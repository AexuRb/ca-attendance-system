package com.ca.attendance.access;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class RolePermissionPolicy {
    private static final Set<Role> MANAGERS = EnumSet.of(Role.MINISTER, Role.PRESIDENT, Role.ADMIN);
    private static final Set<Role> LEADERS = EnumSet.of(Role.PRESIDENT, Role.ADMIN);
    private static final Set<Role> ADMINS = EnumSet.of(Role.ADMIN);
    private static final Map<Permission, Set<Role>> GRANTS = grants();

    private RolePermissionPolicy() {
    }

    public static boolean allows(Role role, Permission permission) {
        return role != null && GRANTS.getOrDefault(permission, Set.of()).contains(role);
    }

    public static void require(Role role, Permission permission, String message) {
        if (!allows(role, permission)) {
            throw ApiException.forbidden(message);
        }
    }

    private static Map<Permission, Set<Role>> grants() {
        EnumMap<Permission, Set<Role>> grants = new EnumMap<>(Permission.class);
        grant(grants, MANAGERS,
                Permission.ATTENDANCE_MANAGE,
                Permission.REPAIR_MANAGE,
                Permission.STATS_VIEW,
                Permission.STATS_EXPORT);
        grant(grants, LEADERS,
                Permission.ATTENDANCE_CREATE,
                Permission.MEMBERS_MANAGE,
                Permission.SCHEDULE_MANAGE,
                Permission.DUTY_SETTINGS_MANAGE,
                Permission.TRAINING_MANAGE,
                Permission.REPAIR_EXPORT,
                Permission.REPAIR_DELETE,
                Permission.CUSTOM_EXPORT,
                Permission.BACKUPS,
                Permission.DATA_CENTER,
                Permission.REMOTE_ADMIN);
        grant(grants, ADMINS,
                Permission.MEMBERS_DELETE,
                Permission.REPAIR_RECYCLE_BIN,
                Permission.BACKUP_ADMIN,
                Permission.OPERATION_LOGS);
        return Map.copyOf(grants);
    }

    private static void grant(Map<Permission, Set<Role>> grants, Set<Role> roles, Permission... permissions) {
        for (Permission permission : permissions) {
            grants.put(permission, Set.copyOf(roles));
        }
    }

    public enum Permission {
        ATTENDANCE_MANAGE,
        ATTENDANCE_CREATE,
        MEMBERS_MANAGE,
        MEMBERS_DELETE,
        SCHEDULE_MANAGE,
        DUTY_SETTINGS_MANAGE,
        TRAINING_MANAGE,
        REPAIR_MANAGE,
        REPAIR_EXPORT,
        REPAIR_DELETE,
        REPAIR_RECYCLE_BIN,
        STATS_VIEW,
        STATS_EXPORT,
        CUSTOM_EXPORT,
        BACKUPS,
        BACKUP_ADMIN,
        DATA_CENTER,
        OPERATION_LOGS,
        REMOTE_ADMIN
    }
}
