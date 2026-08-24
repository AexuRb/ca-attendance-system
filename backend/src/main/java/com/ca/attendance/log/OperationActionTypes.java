package com.ca.attendance.log;

import com.ca.attendance.common.ApiException;

import java.util.Locale;
import java.util.Set;

final class OperationActionTypes {
    private static final Set<String> VALUES = Set.of(
            "CREATE", "UPDATE", "DELETE", "LOGIN", "RESTORE", "EXPORT",
            "CREATE_USER", "IMPORT_USERS", "UPDATE_USER", "UPDATE_PROFILE",
            "RESET_PASSWORD", "DELETE_USER", "BULK_UPDATE_USER_STATUS",
            "CREATE_DUTY_SCHEDULE", "UPDATE_DUTY_SCHEDULE", "ARCHIVE_DUTY_SCHEDULE",
            "IMPORT_DUTY_SCHEDULES", "UPDATE_DUTY_WEEKDAYS", "UPDATE_DUTY_PERIODS",
            "UPDATE_ATTENDANCE_POLICY", "REVIEW_ATTENDANCE", "MANUAL_CREATE_ATTENDANCE",
            "BULK_REVIEW_ATTENDANCE", "MANUAL_UPDATE_ATTENDANCE", "DELETE_ATTENDANCE_RECORD",
            "CREATE_TRAINING", "UPDATE_TRAINING", "ARCHIVE_TRAINING",
            "CREATE_TRAINING_PARTICIPANT", "UPDATE_TRAINING_PARTICIPANT",
            "DELETE_TRAINING_PARTICIPANT", "IMPORT_TRAINING_PARTICIPANTS",
            "CREATE_REPAIR_CASE", "UPDATE_REPAIR_CASE", "DELETE_REPAIR_CASE",
            "RESTORE_REPAIR_CASE", "PURGE_REPAIR_CASE", "EXPORT_CUSTOM_DATA", "EXPORT_DATA",
            "REMOTE_LOGIN_SUCCESS", "REMOTE_LOGIN_FAILURE", "REMOTE_LOGIN_LOCKED",
            "LOCAL_LOGIN_SUCCESS", "LOCAL_LOGIN_FAILURE", "LOCAL_LOGIN_LOCKED",
            "SEED_DEMO_DATA"
    );

    private OperationActionTypes() {
    }

    static String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!VALUES.contains(normalized)) {
            throw ApiException.badRequest("操作类型不合法");
        }
        return normalized;
    }
}
