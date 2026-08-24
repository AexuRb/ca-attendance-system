package com.ca.attendance.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class UserDeletionHistoryGuard {
    private final JdbcTemplate jdbc;

    public UserDeletionHistoryGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> findReferences(long userId) {
        List<String> references = new ArrayList<>();
        addIfPresent(references, "签到与审核记录", hasAttendanceHistory(userId));
        addIfPresent(references, "培训记录", hasTrainingHistory(userId));
        addIfPresent(references, "固定周表", hasScheduleHistory(userId));
        addIfPresent(references, "维修事务", hasRepairHistory(userId));
        addIfPresent(references, "操作日志", hasOperationLogs(userId));
        addIfPresent(references, "成员管理记录", hasMemberManagementHistory(userId));
        addIfPresent(references, "系统设置记录", hasSettingsHistory(userId));
        return List.copyOf(references);
    }

    private boolean hasAttendanceHistory(long userId) {
        return exists("""
                SELECT EXISTS (
                  SELECT 1
                  FROM attendance_records
                  WHERE user_id = ?
                     OR check_in_reviewed_by = ?
                     OR check_out_reviewed_by = ?
                     OR created_by = ?
                     OR updated_by = ?
                )
                """, userId, userId, userId, userId, userId);
    }

    private boolean hasTrainingHistory(long userId) {
        return exists("""
                SELECT EXISTS (
                  SELECT 1
                  FROM training_participants
                  WHERE user_id = ? OR created_by = ? OR updated_by = ?
                ) OR EXISTS (
                  SELECT 1
                  FROM training_sessions
                  WHERE created_by = ? OR updated_by = ?
                )
                """, userId, userId, userId, userId, userId);
    }

    private boolean hasScheduleHistory(long userId) {
        return exists("""
                SELECT EXISTS (
                  SELECT 1
                  FROM duty_schedule_assignees
                  WHERE user_id = ?
                ) OR EXISTS (
                  SELECT 1
                  FROM duty_schedule_slots
                  WHERE created_by = ? OR updated_by = ?
                )
                """, userId, userId, userId);
    }

    private boolean hasRepairHistory(long userId) {
        return exists("""
                SELECT EXISTS (
                  SELECT 1
                  FROM repair_cases
                  WHERE handler_user_id = ?
                     OR created_by = ?
                     OR updated_by = ?
                     OR deleted_by = ?
                )
                """, userId, userId, userId, userId);
    }

    private boolean hasOperationLogs(long userId) {
        return exists("""
                SELECT EXISTS (
                  SELECT 1
                  FROM operation_logs
                  WHERE operator_user_id = ?
                )
                """, userId);
    }

    private boolean hasMemberManagementHistory(long userId) {
        return exists("""
                SELECT EXISTS (
                  SELECT 1
                  FROM users
                  WHERE id <> ?
                    AND (disabled_by = ? OR created_by = ? OR updated_by = ?)
                )
                """, userId, userId, userId, userId);
    }

    private boolean hasSettingsHistory(long userId) {
        return exists("""
                SELECT EXISTS (
                  SELECT 1
                  FROM duty_weekday_settings
                  WHERE updated_by = ?
                ) OR EXISTS (
                  SELECT 1
                  FROM app_settings
                  WHERE updated_by = ?
                )
                """, userId, userId);
    }

    private boolean exists(String sql, Object... arguments) {
        Integer result = jdbc.queryForObject(sql, Integer.class, arguments);
        return result != null && result != 0;
    }

    private void addIfPresent(List<String> references, String label, boolean present) {
        if (present) {
            references.add(label);
        }
    }
}
