package com.ca.attendance.settings;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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
        return jdbc.queryForList("SELECT weekday, weekday_name, enabled FROM duty_weekday_settings ORDER BY weekday");
    }

    public void update(List<Integer> enabledWeekdays) {
        AuthUser current = AuthContext.current();
        if (!current.role().canSetDutyWeekdays()) {
            throw ApiException.forbidden("无权调整值班星期");
        }
        List<Map<String, Object>> before = list();
        for (int i = 1; i <= 7; i++) {
            boolean enabled = enabledWeekdays != null && enabledWeekdays.contains(i);
            jdbc.update("UPDATE duty_weekday_settings SET enabled = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE weekday = ?",
                    enabled, current.id(), i);
        }
        logs.log("UPDATE_DUTY_WEEKDAYS", "duty_weekday_settings", null, before, list(), "调整值班星期");
    }

    public void requireManager() {
        Role role = AuthContext.current().role();
        if (!role.atLeastManager()) {
            throw ApiException.forbidden("无权查看该数据");
        }
    }
}
