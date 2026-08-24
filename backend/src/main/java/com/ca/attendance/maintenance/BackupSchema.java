package com.ca.attendance.maintenance;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class BackupSchema {
    static final int LEGACY_SCHEMA_VERSION = 1;
    static final int SCHEMA_VERSION = 4;

    static final Set<String> LEGACY_REQUIRED_TABLES = Set.of(
            "users",
            "attendance_records",
            "operation_logs",
            "duty_weekday_settings"
    );

    static final List<String> RESTORE_TABLE_ORDER = List.of(
            "users",
            "training_sessions",
            "training_participants",
            "duty_schedule_slots",
            "duty_schedule_assignees",
            "repair_case_sequences",
            "repair_cases",
            "duty_weekday_settings",
            "app_settings",
            "attendance_records",
            "public_attendance_submissions",
            "operation_logs"
    );

    static final List<String> CLEAR_TABLE_ORDER = List.of(
            "operation_logs",
            "public_attendance_submissions",
            "duty_schedule_assignees",
            "duty_schedule_slots",
            "training_participants",
            "training_sessions",
            "repair_case_sequences",
            "repair_cases",
            "attendance_records",
            "app_settings",
            "duty_weekday_settings",
            "users"
    );

    static final Set<String> OPTIONAL_RESTORE_TABLES = Set.of(
            "app_settings",
            "training_sessions",
            "training_participants",
            "duty_schedule_slots",
            "duty_schedule_assignees",
            "repair_case_sequences",
            "repair_cases",
            "public_attendance_submissions"
    );

    static final Map<String, Set<String>> TABLE_COLUMNS = Map.ofEntries(
            Map.entry("users", Set.of(
                    "id", "student_no", "name", "password_hash", "role", "status", "phone", "major", "grade", "qq",
                    "must_change_password", "last_login_at", "disabled_at", "disabled_by", "created_by", "updated_by",
                    "created_at", "updated_at"
            )),
            Map.entry("training_sessions", Set.of(
                    "id", "title", "training_date", "start_time", "end_time", "location", "speaker",
                    "description", "status", "created_by", "updated_by", "created_at", "updated_at"
            )),
            Map.entry("training_participants", Set.of(
                    "id", "session_id", "user_id", "student_no_snapshot", "name_snapshot", "attendance_status",
                    "duration_hours", "remark", "source", "created_by", "updated_by", "created_at", "updated_at"
            )),
            Map.entry("duty_schedule_slots", Set.of(
                    "id", "weekday", "start_time", "end_time", "title", "location", "note", "enabled",
                    "status", "created_by", "updated_by", "created_at", "updated_at"
            )),
            Map.entry("duty_schedule_assignees", Set.of(
                    "id", "slot_id", "user_id", "student_no_snapshot", "name_snapshot", "sort_order", "created_at"
            )),
            Map.entry("repair_case_sequences", Set.of(
                    "sequence_date", "last_value", "updated_at"
            )),
            Map.entry("repair_cases", Set.of(
                    "id", "case_no", "agreement_type", "owner_name", "owner_phone", "owner_org", "device_type",
                    "device_brand", "device_model", "device_serial", "accessories", "fault_description",
                    "service_description", "data_backup_confirmed", "risk_acknowledged", "privacy_acknowledged",
                    "status", "received_at", "completed_at", "handler_user_id", "handler_name_snapshot",
                    "remark", "created_by", "updated_by", "created_at", "updated_at", "deleted_at", "deleted_by"
            )),
            Map.entry("attendance_records", Set.of(
                    "id", "user_id", "student_no_snapshot", "name_snapshot", "duty_date", "duty_weekday", "is_duty_day",
                    "within_duty_period", "require_duty_day", "require_duty_period",
                    "check_in_time", "check_out_time", "check_in_status", "check_out_status",
                    "check_in_reviewed_by", "check_out_reviewed_by", "check_in_reviewed_at", "check_out_reviewed_at",
                    "check_in_reject_reason", "check_out_reject_reason", "duration_minutes", "valid_hours",
                    "effective_status", "source", "manual_reason", "created_by", "updated_by", "created_at", "updated_at"
            )),
            Map.entry("public_attendance_submissions", Set.of(
                    "request_id", "student_no", "record_id", "action", "name", "submitted_at",
                    "review_status", "message", "created_at"
            )),
            Map.entry("operation_logs", Set.of(
                    "id", "operator_user_id", "operator_student_no", "operator_name", "action_type", "target_type",
                    "target_id", "before_data", "after_data", "reason", "ip_address", "user_agent", "created_at"
            )),
            Map.entry("duty_weekday_settings", Set.of(
                    "weekday", "weekday_name", "enabled", "updated_by", "created_at", "updated_at"
            )),
            Map.entry("app_settings", Set.of(
                    "setting_key", "setting_value", "description", "updated_by", "created_at", "updated_at"
            ))
    );

    static final Map<String, Set<String>> REQUIRED_KEYS = Map.ofEntries(
            Map.entry("users", Set.of("id", "student_no", "name", "password_hash", "role", "status")),
            Map.entry("training_sessions", Set.of("id", "title", "training_date", "status")),
            Map.entry("training_participants", Set.of(
                    "id", "session_id", "student_no_snapshot", "name_snapshot", "attendance_status", "source"
            )),
            Map.entry("duty_schedule_slots", Set.of("id", "weekday", "title", "enabled", "status")),
            Map.entry("duty_schedule_assignees", Set.of("id", "slot_id", "name_snapshot", "sort_order")),
            Map.entry("repair_case_sequences", Set.of("sequence_date", "last_value")),
            Map.entry("repair_cases", Set.of(
                    "id", "case_no", "agreement_type", "owner_name", "device_type",
                    "fault_description", "status", "received_at"
            )),
            Map.entry("attendance_records", Set.of(
                    "id", "user_id", "student_no_snapshot", "name_snapshot", "duty_date", "check_in_time"
            )),
            Map.entry("public_attendance_submissions", Set.of(
                    "request_id", "student_no", "record_id", "action", "name", "submitted_at", "review_status", "message"
            )),
            Map.entry("operation_logs", Set.of("id", "action_type", "target_type", "created_at")),
            Map.entry("duty_weekday_settings", Set.of("weekday", "weekday_name", "enabled")),
            Map.entry("app_settings", Set.of("setting_key", "setting_value"))
    );

    static final Map<String, Set<String>> DATE_COLUMNS = Map.ofEntries(
            Map.entry("attendance_records", Set.of("duty_date")),
            Map.entry("training_sessions", Set.of("training_date"))
    );

    static final Map<String, Set<String>> TIME_COLUMNS = Map.ofEntries(
            Map.entry("training_sessions", Set.of("start_time", "end_time")),
            Map.entry("duty_schedule_slots", Set.of("start_time", "end_time"))
    );

    static final Map<String, Set<String>> DATE_TIME_COLUMNS = Map.ofEntries(
            Map.entry("users", Set.of("last_login_at", "disabled_at", "created_at", "updated_at")),
            Map.entry("training_sessions", Set.of("created_at", "updated_at")),
            Map.entry("training_participants", Set.of("created_at", "updated_at")),
            Map.entry("duty_schedule_slots", Set.of("created_at", "updated_at")),
            Map.entry("duty_schedule_assignees", Set.of("created_at")),
            Map.entry("repair_case_sequences", Set.of("updated_at")),
            Map.entry("repair_cases", Set.of("received_at", "completed_at", "created_at", "updated_at", "deleted_at")),
            Map.entry("attendance_records", Set.of(
                    "check_in_time", "check_out_time", "check_in_reviewed_at", "check_out_reviewed_at",
                    "created_at", "updated_at"
            )),
            Map.entry("public_attendance_submissions", Set.of("submitted_at", "created_at")),
            Map.entry("operation_logs", Set.of("created_at")),
            Map.entry("duty_weekday_settings", Set.of("created_at", "updated_at")),
            Map.entry("app_settings", Set.of("created_at", "updated_at"))
    );

    static final Set<String> JSON_COLUMNS = Set.of("before_data", "after_data");

    static Set<String> requiredTables(int schemaVersion) {
        if (schemaVersion < 3) {
            return LEGACY_REQUIRED_TABLES;
        }
        if (schemaVersion == 3) {
            Set<String> required = new java.util.LinkedHashSet<>(RESTORE_TABLE_ORDER);
            required.remove("repair_case_sequences");
            return Set.copyOf(required);
        }
        return Set.copyOf(RESTORE_TABLE_ORDER);
    }

    private BackupSchema() {
    }
}
