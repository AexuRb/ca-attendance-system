package com.ca.attendance.settings;

import com.ca.attendance.access.RolePermissionPolicy;
import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.log.OperationLogService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DutyPeriodService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DutyPeriodService.class);
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
        } catch (Exception ex) {
            LOGGER.error("Stored duty period configuration is invalid", ex);
            throw ApiException.badRequest("值班时间段配置损坏，请联系管理员处理");
        }
    }

    @Transactional
    public List<DutyPeriodItem> update(List<DutyPeriodRequest> periods) {
        AuthUser current = AuthContext.current();
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.DUTY_SETTINGS_MANAGE,
                "无权调整值班时间段");
        List<DutyPeriodItem> before = list();
        List<DutyPeriodItem> normalized = normalize(periods);
        validateActiveScheduleReferences(normalized);
        try {
            String value = objectMapper.writeValueAsString(normalized);
            jdbc.update("""
                    INSERT INTO app_settings (setting_key, setting_value, description, updated_by)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (setting_key) DO UPDATE SET
                      setting_value = excluded.setting_value,
                      description = excluded.description,
                      updated_by = excluded.updated_by,
                      updated_at = datetime('now', 'localtime')
                    """, SETTING_KEY, value, DESCRIPTION, current.id());
            logs.log("UPDATE_DUTY_PERIODS", "app_settings", null, before, normalized, "调整值班时间段");
            return normalized;
        } catch (Exception ex) {
            throw ApiException.badRequest("值班时间段保存失败");
        }
    }

    public boolean contains(LocalTime time) {
        if (time == null) {
            return false;
        }
        return listEnabled().stream().anyMatch(period -> {
            LocalTime start = LocalTime.parse(period.startTime());
            LocalTime end = LocalTime.parse(period.endTime());
            return !time.isBefore(start) && time.isBefore(end);
        });
    }

    public List<DutyPeriodItem> listEnabled() {
        return list().stream().filter(DutyPeriodItem::enabled).toList();
    }

    public void requireManager() {
        RolePermissionPolicy.require(AuthContext.current().role(),
                RolePermissionPolicy.Permission.ATTENDANCE_MANAGE,
                "无权查看该数据");
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
            values.add(new PeriodValue(start, end, period.enabled() == null || period.enabled()));
        }
        if (values.isEmpty()) {
            throw ApiException.badRequest("至少保留一个值班时间段");
        }
        if (values.size() > 12) {
            throw ApiException.badRequest("值班时间段最多设置 12 个");
        }
        List<DutyPeriodItem> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (PeriodValue value : values) {
            String key = value.start().format(TIME_FORMAT) + "-" + value.end().format(TIME_FORMAT);
            if (!keys.add(key)) {
                throw ApiException.badRequest("值班时间段不能重复");
            }
            result.add(new DutyPeriodItem(
                    result.size(),
                    value.start().format(TIME_FORMAT),
                    value.end().format(TIME_FORMAT),
                    value.enabled()
            ));
        }
        validateOverlaps(values);
        return result;
    }

    private void validateOverlaps(List<PeriodValue> values) {
        List<PeriodValue> enabled = values.stream()
                .filter(PeriodValue::enabled)
                .sorted(Comparator.comparing(PeriodValue::start).thenComparing(PeriodValue::end))
                .toList();
        for (int index = 1; index < enabled.size(); index++) {
            if (enabled.get(index).start().isBefore(enabled.get(index - 1).end())) {
                throw ApiException.badRequest("启用中的值班时间段不能重叠");
            }
        }
    }

    private void validateActiveScheduleReferences(List<DutyPeriodItem> periods) {
        Set<String> enabledKeys = periods.stream()
                .filter(DutyPeriodItem::enabled)
                .map(period -> period.startTime() + "-" + period.endTime())
                .collect(java.util.stream.Collectors.toSet());
        List<String> usedKeys = jdbc.query("""
                SELECT DISTINCT start_time, end_time
                FROM duty_schedule_slots
                WHERE status = 'ACTIVE'
                  AND enabled = 1
                """, (rs, rowNum) -> periodKey(
                LocalTime.parse(rs.getString("start_time")),
                LocalTime.parse(rs.getString("end_time"))
        ));
        List<String> conflicts = usedKeys.stream().filter(key -> !enabledKeys.contains(key)).toList();
        if (!conflicts.isEmpty()) {
            throw ApiException.badRequest(
                    "值班时间段仍被固定排班使用，请先调整对应排班：" + String.join("、", conflicts));
        }
    }

    private String periodKey(LocalTime start, LocalTime end) {
        return start.format(TIME_FORMAT) + "-" + end.format(TIME_FORMAT);
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
    public record DutyPeriodRequest(String startTime, String endTime, Boolean enabled) {
        public DutyPeriodRequest(String startTime, String endTime) {
            this(startTime, endTime, true);
        }
    }

    private record PeriodValue(LocalTime start, LocalTime end, boolean enabled) {
    }
}
