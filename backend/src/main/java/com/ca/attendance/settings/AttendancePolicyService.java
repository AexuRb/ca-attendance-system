package com.ca.attendance.settings;

import com.ca.attendance.access.RolePermissionPolicy;
import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class AttendancePolicyService {
    private static final String REQUIRE_DUTY_DAY_KEY = "ATTENDANCE_REQUIRE_DUTY_DAY";
    private static final String REQUIRE_DUTY_PERIOD_KEY = "ATTENDANCE_REQUIRE_DUTY_PERIOD";

    private final JdbcTemplate jdbc;
    private final OperationLogService logs;

    public AttendancePolicyService(JdbcTemplate jdbc, OperationLogService logs) {
        this.jdbc = jdbc;
        this.logs = logs;
    }

    public AttendancePolicy current() {
        Map<String, String> values = new HashMap<>();
        jdbc.queryForList("""
                SELECT setting_key, setting_value
                FROM app_settings
                WHERE setting_key IN (?, ?)
                """, REQUIRE_DUTY_DAY_KEY, REQUIRE_DUTY_PERIOD_KEY).forEach(row -> values.put(
                String.valueOf(row.get("setting_key")),
                String.valueOf(row.get("setting_value"))
        ));
        return new AttendancePolicy(
                enabled(values.get(REQUIRE_DUTY_DAY_KEY)),
                enabled(values.get(REQUIRE_DUTY_PERIOD_KEY))
        );
    }

    public AttendancePolicy readForManager() {
        RolePermissionPolicy.require(
                AuthContext.current().role(),
                RolePermissionPolicy.Permission.DUTY_SETTINGS_MANAGE,
                "无权查看有效时长规则"
        );
        return current();
    }

    @Transactional
    public AttendancePolicy update(UpdateAttendancePolicyRequest request) {
        AuthUser current = AuthContext.current();
        if (current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有管理员可以修改有效时长规则");
        }
        AttendancePolicy before = current();
        AttendancePolicy after = new AttendancePolicy(
                Boolean.TRUE.equals(request.requireDutyDay()),
                Boolean.TRUE.equals(request.requireDutyPeriod())
        );
        save(REQUIRE_DUTY_DAY_KEY, after.requireDutyDay(), "有效时长是否强制要求值班日", current.id());
        save(REQUIRE_DUTY_PERIOD_KEY, after.requireDutyPeriod(), "有效时长是否强制要求值班时段", current.id());
        logs.log(
                "UPDATE_ATTENDANCE_POLICY",
                "app_settings",
                null,
                before,
                after,
                "调整有效时长规则"
        );
        return after;
    }

    private void save(String key, boolean value, String description, long operatorId) {
        jdbc.update("""
                INSERT INTO app_settings (setting_key, setting_value, description, updated_by)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (setting_key) DO UPDATE SET
                  setting_value = excluded.setting_value,
                  description = excluded.description,
                  updated_by = excluded.updated_by,
                  updated_at = datetime('now', 'localtime')
                """, key, Boolean.toString(value), description, operatorId);
    }

    private boolean enabled(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    public record AttendancePolicy(boolean requireDutyDay, boolean requireDutyPeriod) {
    }

    public record UpdateAttendancePolicyRequest(
            @NotNull Boolean requireDutyDay,
            @NotNull Boolean requireDutyPeriod
    ) {
    }
}
