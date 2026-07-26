package com.ca.attendance.schedule;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.settings.DutyPeriodItem;
import com.ca.attendance.settings.DutyPeriodService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Pattern;

import static com.ca.attendance.common.JdbcTime.localDateTime;
import static com.ca.attendance.common.JdbcTime.localTime;
import static com.ca.attendance.common.JdbcTime.databaseTime;

@Service
public class DutyScheduleService {
    private static final Pattern STUDENT_NO_PATTERN = Pattern.compile("\\d{1,32}");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbc;
    private final OperationLogService logs;
    private final DutyPeriodService dutyPeriods;

    private final RowMapper<SlotRow> slotMapper = (rs, rowNum) -> new SlotRow(
            rs.getLong("id"),
            rs.getInt("weekday"),
            localTime(rs, "start_time"),
            localTime(rs, "end_time"),
            rs.getString("title"),
            rs.getString("location"),
            rs.getString("note"),
            rs.getBoolean("enabled"),
            rs.getString("created_by_name"),
            rs.getString("updated_by_name"),
            localDateTime(rs, "created_at"),
            localDateTime(rs, "updated_at")
    );

    public DutyScheduleService(JdbcTemplate jdbc, OperationLogService logs, DutyPeriodService dutyPeriods) {
        this.jdbc = jdbc;
        this.logs = logs;
        this.dutyPeriods = dutyPeriods;
    }

    public List<DutyScheduleSlotItem> list() {
        AuthContext.current();
        return slots("""
                WHERE s.status = 'ACTIVE'
                ORDER BY s.weekday, COALESCE(s.start_time, '00:00:00'), s.id
                """, null);
    }

    public List<DutyScheduleSlotItem> today(LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return slots("""
                WHERE s.status = 'ACTIVE'
                  AND s.enabled = 1
                  AND s.weekday = ?
                ORDER BY COALESCE(s.start_time, '00:00:00'), s.id
                """, monday, date.getDayOfWeek().getValue());
    }

    public List<DutyScheduleSlotItem> week(LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return slots("""
                WHERE s.status = 'ACTIVE'
                  AND s.enabled = 1
                ORDER BY s.weekday, COALESCE(s.start_time, '00:00:00'), s.id
                """, monday);
    }

    public DutyScheduleSlotItem create(SlotRequest request) {
        AuthUser current = AuthContext.current();
        requireManage(current);
        SlotValues values = slotValues(request, null);
        Long id = jdbc.queryForObject("""
                INSERT INTO duty_schedule_slots (
                  weekday, start_time, end_time, title, location, note, enabled, created_by, updated_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                values.weekday(),
                toSqlTime(values.startTime()),
                toSqlTime(values.endTime()),
                values.title(),
                values.location(),
                values.note(),
                values.enabled(),
                current.id(),
                current.id()
        );
        long slotId = id == null ? 0 : id;
        replaceAssignees(slotId, values.assignees());
        DutyScheduleSlotItem created = find(slotId).orElseThrow();
        logs.log("CREATE_DUTY_SCHEDULE", "duty_schedule_slots", created.id(), null, created, "新增排班");
        return created;
    }

    public DutyScheduleSlotItem update(long id, SlotRequest request) {
        AuthUser current = AuthContext.current();
        requireManage(current);
        DutyScheduleSlotItem before = find(id).orElseThrow(() -> ApiException.notFound("排班不存在"));
        SlotValues values = slotValues(request, before);
        jdbc.update("""
                UPDATE duty_schedule_slots
                SET weekday = ?, start_time = ?, end_time = ?, title = ?, location = ?, note = ?,
                    enabled = ?, updated_by = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ?
                """,
                values.weekday(),
                toSqlTime(values.startTime()),
                toSqlTime(values.endTime()),
                values.title(),
                values.location(),
                values.note(),
                values.enabled(),
                current.id(),
                id
        );
        replaceAssignees(id, values.assignees());
        DutyScheduleSlotItem after = find(id).orElseThrow();
        logs.log("UPDATE_DUTY_SCHEDULE", "duty_schedule_slots", id, before, after, "修改排班");
        return after;
    }

    public void archive(long id) {
        AuthUser current = AuthContext.current();
        requireManage(current);
        DutyScheduleSlotItem before = find(id).orElseThrow(() -> ApiException.notFound("排班不存在"));
        jdbc.update("""
                UPDATE duty_schedule_slots
                SET status = 'ARCHIVED', updated_by = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ?
                """, current.id(), id);
        logs.log("ARCHIVE_DUTY_SCHEDULE", "duty_schedule_slots", id, before, Map.of("status", "ARCHIVED"), "归档排班");
    }

    private Optional<DutyScheduleSlotItem> find(long id) {
        return slots("""
                WHERE s.id = ?
                ORDER BY s.weekday, COALESCE(s.start_time, '00:00:00'), s.id
                """, null, id).stream().findFirst();
    }

    private List<DutyScheduleSlotItem> slots(String whereSql, LocalDate weekStart, Object... args) {
        List<SlotRow> rows = jdbc.query("""
                SELECT s.*,
                       cb.name AS created_by_name,
                       ub.name AS updated_by_name
                FROM duty_schedule_slots s
                LEFT JOIN users cb ON cb.id = s.created_by
                LEFT JOIN users ub ON ub.id = s.updated_by
                """ + whereSql + """

                """, slotMapper, args);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<DutyScheduleSlotItem.AssigneeItem>> assignees = assignees(rows.stream().map(SlotRow::id).toList());
        return rows.stream().map(row -> new DutyScheduleSlotItem(
                row.id(),
                row.weekday(),
                weekdayName(row.weekday()),
                weekStart == null ? null : weekStart.plusDays(row.weekday() - 1L),
                row.startTime(),
                row.endTime(),
                row.title(),
                row.location(),
                row.note(),
                row.enabled(),
                assignees.getOrDefault(row.id(), List.of()),
                row.createdByName(),
                row.updatedByName(),
                row.createdAt(),
                row.updatedAt()
        )).toList();
    }

    private Map<Long, List<DutyScheduleSlotItem.AssigneeItem>> assignees(List<Long> slotIds) {
        if (slotIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(slotIds.size(), "?"));
        Map<Long, List<DutyScheduleSlotItem.AssigneeItem>> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT id, slot_id, user_id, student_no_snapshot, name_snapshot, sort_order
                FROM duty_schedule_assignees
                WHERE slot_id IN (""" + placeholders + """
                )
                ORDER BY slot_id, sort_order, id
                """, rs -> {
            long slotId = rs.getLong("slot_id");
            result.computeIfAbsent(slotId, ignored -> new ArrayList<>()).add(new DutyScheduleSlotItem.AssigneeItem(
                    rs.getLong("id"),
                    nullableLong(rs, "user_id"),
                    rs.getString("student_no_snapshot"),
                    rs.getString("name_snapshot"),
                    rs.getInt("sort_order")
            ));
        }, slotIds.toArray());
        return result;
    }

    private void replaceAssignees(long slotId, List<AssigneeValues> assignees) {
        jdbc.update("DELETE FROM duty_schedule_assignees WHERE slot_id = ?", slotId);
        int order = 0;
        for (AssigneeValues assignee : assignees) {
            jdbc.update("""
                    INSERT INTO duty_schedule_assignees (
                      slot_id, user_id, student_no_snapshot, name_snapshot, sort_order
                    )
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    slotId,
                    assignee.userId(),
                    assignee.studentNo(),
                    assignee.name(),
                    order++
            );
        }
    }

    private SlotValues slotValues(SlotRequest request, DutyScheduleSlotItem fallback) {
        int weekday = request.weekday() == null && fallback != null ? fallback.weekday() : number(request.weekday(), "星期不能为空");
        if (weekday < 1 || weekday > 7) {
            throw ApiException.badRequest("星期必须在 1 到 7 之间");
        }
        LocalTime startTime = request.startTime() == null && fallback != null ? fallback.startTime() : request.startTime();
        LocalTime endTime = request.endTime() == null && fallback != null ? fallback.endTime() : request.endTime();
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw ApiException.badRequest("结束时间不能早于开始时间");
        }
        requireConfiguredDutyPeriod(startTime, endTime);
        String title = request.title() == null && fallback != null ? fallback.title() : request.title();
        Boolean enabled = request.enabled() == null && fallback != null ? fallback.enabled() : request.enabled();
        List<AssigneeValues> assignees = normalizeAssignees(request.assignees());
        return new SlotValues(
                weekday,
                startTime,
                endTime,
                required(title, "排班标题不能为空", 100),
                trimToNull(request.location() == null && fallback != null ? fallback.location() : request.location(), 120),
                trimToNull(request.note() == null && fallback != null ? fallback.note() : request.note(), 500),
                enabled == null || enabled,
                assignees
        );
    }

    private void requireConfiguredDutyPeriod(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            throw ApiException.badRequest("请选择已设置的值班时间段");
        }
        String selectedKey = periodKey(startTime, endTime);
        List<DutyPeriodItem> configuredPeriods = dutyPeriods.list();
        if (configuredPeriods.isEmpty()) {
            throw ApiException.badRequest("请先在值班设置中保存值班时间段");
        }
        boolean matched = configuredPeriods.stream()
                .anyMatch(period -> selectedKey.equals(period.startTime() + "-" + period.endTime()));
        if (!matched) {
            throw ApiException.badRequest("排班时间必须使用值班设置中已有的时间段");
        }
    }

    private String periodKey(LocalTime startTime, LocalTime endTime) {
        return startTime.format(TIME_FORMAT) + "-" + endTime.format(TIME_FORMAT);
    }

    private List<AssigneeValues> normalizeAssignees(List<AssigneeRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        List<AssigneeValues> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AssigneeRequest request : requests) {
            String studentNo = trimToNull(request.studentNo(), 32);
            String name = trimToNull(request.name(), 64);
            if (studentNo == null && name == null) {
                continue;
            }
            if (studentNo != null && !STUDENT_NO_PATTERN.matcher(studentNo).matches()) {
                throw ApiException.badRequest("排班成员学号格式不正确：" + studentNo);
            }
            UserRef user = studentNo == null ? null : findUser(studentNo).orElse(null);
            String displayName = user == null ? name : user.name();
            if (displayName == null) {
                throw ApiException.badRequest("排班成员姓名不能为空");
            }
            String key = studentNo == null ? "name:" + displayName : "student:" + studentNo;
            if (!seen.add(key)) {
                continue;
            }
            result.add(new AssigneeValues(
                    user == null ? null : user.id(),
                    user == null ? studentNo : user.studentNo(),
                    displayName
            ));
        }
        return result;
    }

    private Optional<UserRef> findUser(String studentNo) {
        return jdbc.query("""
                SELECT id, student_no, name
                FROM users
                WHERE student_no = ?
                LIMIT 1
                """, (rs, rowNum) -> new UserRef(
                rs.getLong("id"),
                rs.getString("student_no"),
                rs.getString("name")
        ), studentNo).stream().findFirst();
    }

    private void requireManage(AuthUser current) {
        if (current.role() != Role.PRESIDENT && current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有会长或管理员可以管理排班");
        }
    }

    private int number(Integer value, String message) {
        if (value == null) {
            throw ApiException.badRequest(message);
        }
        return value;
    }

    private String required(String value, String message, int maxLength) {
        String text = trimToNull(value, maxLength);
        if (text == null) {
            throw ApiException.badRequest(message);
        }
        return text;
    }

    private String trimToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
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
            default -> "未知";
        };
    }

    private String toSqlTime(LocalTime time) {
        return databaseTime(time);
    }

    private Long nullableLong(ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record SlotRequest(
            Integer weekday,
            LocalTime startTime,
            LocalTime endTime,
            String title,
            String location,
            String note,
            Boolean enabled,
            List<AssigneeRequest> assignees
    ) {
    }

    public record AssigneeRequest(String studentNo, String name) {
    }

    private record SlotRow(
            long id,
            int weekday,
            LocalTime startTime,
            LocalTime endTime,
            String title,
            String location,
            String note,
            boolean enabled,
            String createdByName,
            String updatedByName,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt
    ) {
    }

    private record SlotValues(
            int weekday,
            LocalTime startTime,
            LocalTime endTime,
            String title,
            String location,
            String note,
            boolean enabled,
            List<AssigneeValues> assignees
    ) {
    }

    private record AssigneeValues(Long userId, String studentNo, String name) {
    }

    private record UserRef(long id, String studentNo, String name) {
    }
}
