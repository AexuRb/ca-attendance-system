package com.ca.attendance.settings;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.log.OperationLogService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DutyPeriodService {
    private static final String SETTING_KEY = "DUTY_TIME_PERIODS";
    private static final String DESCRIPTION = "签到台按这些值班时间段汇总部长排班人数";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final OperationLogService logs;

    public DutyPeriodService(JdbcTemplate jdbc, ObjectMapper objectMapper, OperationLogService logs) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.logs = logs;
    }

    public List<DutyPeriodItem> list() {
        List<String> values = jdbc.queryForList("""
                SELECT setting_value
                FROM app_settings
                WHERE setting_key = ?
                LIMIT 1
                """, String.class, SETTING_KEY);
        if (values.isEmpty() || values.get(0) == null || values.get(0).isBlank()) {
            return List.of();
        }
        try {
            List<DutyPeriodRequest> periods = objectMapper.readValue(values.get(0), new TypeReference<>() {
            });
            return normalize(periods);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public List<DutyPeriodItem> update(List<DutyPeriodRequest> periods) {
        AuthUser current = AuthContext.current();
        if (!current.role().canSetDutyWeekdays()) {
            throw ApiException.forbidden("无权调整值班时间段");
        }
        List<DutyPeriodItem> before = list();
        List<DutyPeriodItem> normalized = normalize(periods);
        try {
            String value = objectMapper.writeValueAsString(normalized);
            jdbc.update("""
                    INSERT INTO app_settings (setting_key, setting_value, description, updated_by)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (setting_key) DO UPDATE SET
                      setting_value = excluded.setting_value,
                      description = excluded.description,
                      updated_by = excluded.updated_by,
                      updated_at = CURRENT_TIMESTAMP
                    """, SETTING_KEY, value, DESCRIPTION, current.id());
            logs.log("UPDATE_DUTY_PERIODS", "app_settings", null, before, normalized, "调整值班时间段");
            return normalized;
        } catch (Exception ex) {
            throw ApiException.badRequest("值班时间段保存失败");
        }
    }

    public void requireManager() {
        if (!AuthContext.current().role().atLeastManager()) {
            throw ApiException.forbidden("无权查看该数据");
        }
    }

    private List<DutyPeriodItem> normalize(List<DutyPeriodRequest> periods) {
        if (periods == null || periods.isEmpty()) {
            throw ApiException.badRequest("至少保留一个值班时间段");
        }
        List<PeriodValue> values = new ArrayList<>();
        for (DutyPeriodRequest period : periods) {
            if (period == null) {
                continue;
            }
            LocalTime start = parseTime(period.startTime(), "开始时间不能为空");
            LocalTime end = parseTime(period.endTime(), "结束时间不能为空");
            if (!end.isAfter(start)) {
                throw ApiException.badRequest("结束时间必须晚于开始时间");
            }
            values.add(new PeriodValue(start, end));
        }
        if (values.isEmpty()) {
            throw ApiException.badRequest("至少保留一个值班时间段");
        }
        if (values.size() > 12) {
            throw ApiException.badRequest("值班时间段最多设置 12 个");
        }
        values.sort(Comparator.comparing(PeriodValue::start).thenComparing(PeriodValue::end));
        List<DutyPeriodItem> result = new ArrayList<>();
        String lastKey = "";
        for (PeriodValue value : values) {
            String key = value.start().format(TIME_FORMAT) + "-" + value.end().format(TIME_FORMAT);
            if (key.equals(lastKey)) {
                continue;
            }
            result.add(new DutyPeriodItem(result.size(), value.start().format(TIME_FORMAT), value.end().format(TIME_FORMAT)));
            lastKey = key;
        }
        return result;
    }

    private LocalTime parseTime(String value, String emptyMessage) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(emptyMessage);
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (Exception ex) {
            throw ApiException.badRequest("时间格式应为 HH:mm");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DutyPeriodRequest(String startTime, String endTime) {
    }

    private record PeriodValue(LocalTime start, LocalTime end) {
    }
}
