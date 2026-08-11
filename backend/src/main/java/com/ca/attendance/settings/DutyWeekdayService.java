package com.ca.attendance.settings;

import com.ca.attendance.access.RolePermissionPolicy;
import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class DutyWeekdayService {
    private final JdbcTemplate jdbc;
    private final OperationLogService logs;

    public DutyWeekdayService(JdbcTemplate jdbc, OperationLogService logs) {
        this.jdbc = jdbc;
        this.logs = logs;
    }

    public boolean isDutyWeekday(int weekday) {
        Boolean enabled = jdbc.queryForObject("SELECT enabled FROM duty_weekday_settings WHERE weekday = ?", Boolean.class, weekday);
        return Boolean.TRUE.equals(enabled);
    }

    public List<Map<String, Object>> list() {
        return jdbc.queryForList("SELECT weekday, weekday_name, enabled FROM duty_weekday_settings ORDER BY weekday")
                .stream()
                .map(this::normalizeRow)
                .toList();
    }

    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("weekday", ((Number) row.get("weekday")).intValue());
        normalized.put("weekday_name", row.get("weekday_name"));
        Object enabled = row.get("enabled");
        normalized.put("enabled", enabled instanceof Boolean booleanValue
                ? booleanValue
                : enabled instanceof Number numberValue && numberValue.intValue() == 1);
        return normalized;
    }

    @Transactional
    public void update(List<Integer> enabledWeekdays) {
        AuthUser current = AuthContext.current();
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.DUTY_SETTINGS_MANAGE,
                "无权调整值班星期");
        Set<Integer> normalized = normalizeWeekdays(enabledWeekdays);
        validateActiveScheduleReferences(normalized);
        List<Map<String, Object>> before = list();
        for (int i = 1; i <= 7; i++) {
            boolean enabled = normalized.contains(i);
            jdbc.update("UPDATE duty_weekday_settings SET enabled = ?, updated_by = ?, updated_at = datetime('now', 'localtime') WHERE weekday = ?",
                    enabled, current.id(), i);
        }
        logs.log("UPDATE_DUTY_WEEKDAYS", "duty_weekday_settings", null, before, list(), "调整值班星期");
    }

    private Set<Integer> normalizeWeekdays(List<Integer> enabledWeekdays) {
        Set<Integer> normalized = new LinkedHashSet<>();
        if (enabledWeekdays == null) {
            return normalized;
        }
        for (Integer weekday : enabledWeekdays) {
            if (weekday == null || weekday < 1 || weekday > 7) {
                throw ApiException.badRequest("值班星期必须在 1 到 7 之间");
            }
            normalized.add(weekday);
        }
        return normalized;
    }

    private void validateActiveScheduleReferences(Set<Integer> enabledWeekdays) {
        List<Integer> referenced = jdbc.queryForList("""
                SELECT DISTINCT weekday
                FROM duty_schedule_slots
                WHERE status = 'ACTIVE'
                  AND enabled = 1
                ORDER BY weekday
                """, Integer.class);
        List<String> conflicts = referenced.stream()
                .filter(weekday -> !enabledWeekdays.contains(weekday))
                .map(this::weekdayName)
                .toList();
        if (!conflicts.isEmpty()) {
            throw ApiException.badRequest(
                    "值班星期仍被固定排班使用，请先调整对应排班：" + String.join("、", conflicts));
        }
    }

    private String weekdayName(int weekday) {
        return switch (weekday) {
            case 1 -> "星期一";
            case 2 -> "星期二";
            case 3 -> "星期三";
            case 4 -> "星期四";
            case 5 -> "星期五";
            case 6 -> "星期六";
            case 7 -> "星期日";
            default -> "未知星期";
        };
    }

    public void requireManager() {
        RolePermissionPolicy.require(AuthContext.current().role(),
                RolePermissionPolicy.Permission.ATTENDANCE_MANAGE,
                "无权查看该数据");
    }
}
