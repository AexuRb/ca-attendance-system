package com.ca.attendance.schedule.infrastructure;

import com.ca.attendance.schedule.domain.EffectiveScheduleDay;
import com.ca.attendance.schedule.domain.ScheduleAdjustmentType;
import com.ca.attendance.schedule.domain.ScheduleAssignee;
import com.ca.attendance.schedule.domain.ScheduleException;
import com.ca.attendance.schedule.domain.ShiftReassignment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ca.attendance.common.JdbcTime.databaseDate;
import static com.ca.attendance.common.JdbcTime.databaseTime;
import static com.ca.attendance.common.JdbcTime.localDate;
import static com.ca.attendance.common.JdbcTime.localDateTime;
import static com.ca.attendance.common.JdbcTime.localTime;

@Repository
public class ScheduleAdjustmentRepository {
    private final JdbcTemplate jdbc;

    public ScheduleAdjustmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<EffectiveScheduleDay.EffectiveSlot> baseSlots(long termId, int weekday) {
        List<SlotRow> rows = jdbc.query("""
                SELECT id, start_time, end_time, title, location, note
                FROM duty_schedule_slots
                WHERE term_id = ? AND weekday = ? AND status = 'ACTIVE' AND enabled = 1
                ORDER BY start_time, end_time, id
                """, (rs, rowNum) -> new SlotRow(
                rs.getLong("id"), localTime(rs, "start_time"), localTime(rs, "end_time"),
                rs.getString("title"), rs.getString("location"), rs.getString("note")
        ), termId, weekday);
        Map<Long, List<ScheduleAssignee>> assignees = slotAssignees(rows.stream().map(SlotRow::id).toList());
        return rows.stream().map(row -> new EffectiveScheduleDay.EffectiveSlot(
                "slot-" + row.id(), row.id(), null, row.startTime(), row.endTime(), row.title(),
                row.location(), row.note(), "RECURRING", assignees.getOrDefault(row.id(), List.of())
        )).toList();
    }

    public Optional<EffectiveScheduleDay.EffectiveSlot> baseSlot(long termId, long slotId) {
        return baseSlotsForIds(termId, List.of(slotId)).stream().findFirst();
    }

    private List<EffectiveScheduleDay.EffectiveSlot> baseSlotsForIds(long termId, List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(termId);
        args.addAll(ids);
        List<SlotRow> rows = jdbc.query("""
                SELECT id, start_time, end_time, title, location, note
                FROM duty_schedule_slots
                WHERE term_id = ? AND status = 'ACTIVE' AND id IN (""" + placeholders + ")",
                (rs, rowNum) -> new SlotRow(
                        rs.getLong("id"), localTime(rs, "start_time"), localTime(rs, "end_time"),
                        rs.getString("title"), rs.getString("location"), rs.getString("note")
                ), args.toArray());
        Map<Long, List<ScheduleAssignee>> assignees = slotAssignees(ids);
        return rows.stream().map(row -> new EffectiveScheduleDay.EffectiveSlot(
                "slot-" + row.id(), row.id(), null, row.startTime(), row.endTime(), row.title(),
                row.location(), row.note(), "RECURRING", assignees.getOrDefault(row.id(), List.of())
        )).toList();
    }

    public List<ScheduleException> exceptions(long termId, LocalDate from, LocalDate to) {
        List<ExceptionRow> rows = jdbc.query("""
                SELECT e.*, cb.name AS created_by_name, ub.name AS updated_by_name
                FROM duty_schedule_exceptions e
                LEFT JOIN users cb ON cb.id = e.created_by
                LEFT JOIN users ub ON ub.id = e.updated_by
                WHERE e.term_id = ? AND e.exception_date BETWEEN ? AND ?
                ORDER BY e.exception_date, e.start_time, e.id
                """, (rs, rowNum) -> new ExceptionRow(
                rs.getLong("id"), rs.getLong("term_id"), localDate(rs, "exception_date"),
                ScheduleAdjustmentType.valueOf(rs.getString("exception_type")), nullableLong(rs, "source_slot_id"),
                localTime(rs, "start_time"), localTime(rs, "end_time"), rs.getString("title"),
                rs.getString("location"), rs.getString("reason"), rs.getString("created_by_name"),
                rs.getString("updated_by_name"), localDateTime(rs, "created_at"), localDateTime(rs, "updated_at")
        ), termId, databaseDate(from), databaseDate(to));
        Map<Long, List<ScheduleAssignee>> assignees = exceptionAssignees(rows.stream().map(ExceptionRow::id).toList());
        return rows.stream().map(row -> row.toDomain(assignees.getOrDefault(row.id(), List.of()))).toList();
    }

    public Optional<ScheduleException> exception(long id) {
        List<ExceptionRow> rows = jdbc.query("""
                SELECT e.*, cb.name AS created_by_name, ub.name AS updated_by_name
                FROM duty_schedule_exceptions e
                LEFT JOIN users cb ON cb.id = e.created_by
                LEFT JOIN users ub ON ub.id = e.updated_by
                WHERE e.id = ?
                """, (rs, rowNum) -> new ExceptionRow(
                rs.getLong("id"), rs.getLong("term_id"), localDate(rs, "exception_date"),
                ScheduleAdjustmentType.valueOf(rs.getString("exception_type")), nullableLong(rs, "source_slot_id"),
                localTime(rs, "start_time"), localTime(rs, "end_time"), rs.getString("title"),
                rs.getString("location"), rs.getString("reason"), rs.getString("created_by_name"),
                rs.getString("updated_by_name"), localDateTime(rs, "created_at"), localDateTime(rs, "updated_at")
        ), id);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        ExceptionRow row = rows.getFirst();
        return Optional.of(row.toDomain(exceptionAssignees(List.of(id)).getOrDefault(id, List.of())));
    }

    public long insertException(long termId, LocalDate date, ScheduleAdjustmentType type, Long sourceSlotId,
                                LocalTime start, LocalTime end, String title, String location, String reason,
                                long actorId, List<ScheduleAssignee> assignees) {
        Long id = jdbc.queryForObject("""
                INSERT INTO duty_schedule_exceptions (
                  term_id, exception_date, exception_type, source_slot_id, start_time, end_time,
                  title, location, reason, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, termId, databaseDate(date), type.name(), sourceSlotId,
                databaseTime(start), databaseTime(end), title, location, reason, actorId, actorId);
        long value = id == null ? 0 : id;
        replaceExceptionAssignees(value, assignees);
        return value;
    }

    public void updateException(long id, long termId, LocalDate date, ScheduleAdjustmentType type,
                                Long sourceSlotId, LocalTime start, LocalTime end, String title,
                                String location, String reason, long actorId, List<ScheduleAssignee> assignees) {
        jdbc.update("""
                UPDATE duty_schedule_exceptions
                SET term_id = ?, exception_date = ?, exception_type = ?, source_slot_id = ?,
                    start_time = ?, end_time = ?, title = ?, location = ?, reason = ?,
                    updated_by = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ?
                """, termId, databaseDate(date), type.name(), sourceSlotId,
                databaseTime(start), databaseTime(end), title, location, reason, actorId, id);
        replaceExceptionAssignees(id, assignees);
    }

    public void deleteException(long id) {
        jdbc.update("DELETE FROM duty_schedule_exceptions WHERE id = ?", id);
    }

    public boolean hasDayCancellation(long termId, LocalDate date, Long excludedId) {
        Integer count = excludedId == null
                ? jdbc.queryForObject("""
                    SELECT COUNT(*) FROM duty_schedule_exceptions
                    WHERE term_id = ? AND exception_date = ? AND exception_type = 'DAY_CANCELLED'
                    """, Integer.class, termId, databaseDate(date))
                : jdbc.queryForObject("""
                    SELECT COUNT(*) FROM duty_schedule_exceptions
                    WHERE term_id = ? AND exception_date = ? AND exception_type = 'DAY_CANCELLED' AND id <> ?
                    """, Integer.class, termId, databaseDate(date), excludedId);
        return count != null && count > 0;
    }

    public boolean hasOtherExceptions(long termId, LocalDate date, Long excludedId) {
        Integer count = excludedId == null
                ? jdbc.queryForObject("""
                    SELECT COUNT(*) FROM duty_schedule_exceptions
                    WHERE term_id = ? AND exception_date = ? AND exception_type <> 'DAY_CANCELLED'
                    """, Integer.class, termId, databaseDate(date))
                : jdbc.queryForObject("""
                    SELECT COUNT(*) FROM duty_schedule_exceptions
                    WHERE term_id = ? AND exception_date = ? AND exception_type <> 'DAY_CANCELLED' AND id <> ?
                    """, Integer.class, termId, databaseDate(date), excludedId);
        return count != null && count > 0;
    }

    public List<ShiftReassignment> reassignments(long termId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT r.*, cb.name AS created_by_name, ub.name AS updated_by_name
                FROM duty_shift_reassignments r
                LEFT JOIN users cb ON cb.id = r.created_by
                LEFT JOIN users ub ON ub.id = r.updated_by
                WHERE r.term_id = ? AND r.duty_date BETWEEN ? AND ?
                ORDER BY r.duty_date, r.start_time, r.id
                """, this::mapReassignment, termId, databaseDate(from), databaseDate(to));
    }

    public Optional<ShiftReassignment> reassignment(long id) {
        return jdbc.query("""
                SELECT r.*, cb.name AS created_by_name, ub.name AS updated_by_name
                FROM duty_shift_reassignments r
                LEFT JOIN users cb ON cb.id = r.created_by
                LEFT JOIN users ub ON ub.id = r.updated_by
                WHERE r.id = ?
                """, this::mapReassignment, id).stream().findFirst();
    }

    public boolean reassignmentExists(long termId, LocalDate date, LocalTime start, LocalTime end,
                                      long originalUserId, Long excludedId) {
        Integer count = excludedId == null
                ? jdbc.queryForObject("""
                    SELECT COUNT(*) FROM duty_shift_reassignments
                    WHERE term_id = ? AND duty_date = ? AND start_time = ? AND end_time = ?
                      AND original_user_id = ?
                    """, Integer.class, termId, databaseDate(date), databaseTime(start), databaseTime(end), originalUserId)
                : jdbc.queryForObject("""
                    SELECT COUNT(*) FROM duty_shift_reassignments
                    WHERE term_id = ? AND duty_date = ? AND start_time = ? AND end_time = ?
                      AND original_user_id = ? AND id <> ?
                    """, Integer.class, termId, databaseDate(date), databaseTime(start), databaseTime(end),
                    originalUserId, excludedId);
        return count != null && count > 0;
    }

    public long insertReassignment(long termId, LocalDate date, Long sourceSlotId,
                                   LocalTime start, LocalTime end, UserRef original,
                                   UserRef replacement, String reason, long actorId) {
        Long id = jdbc.queryForObject("""
                INSERT INTO duty_shift_reassignments (
                  term_id, duty_date, source_slot_id, start_time, end_time,
                  original_user_id, original_student_no_snapshot, original_name_snapshot,
                  replacement_user_id, replacement_student_no_snapshot, replacement_name_snapshot,
                  reason, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, termId, databaseDate(date), sourceSlotId,
                databaseTime(start), databaseTime(end), original.id(), original.studentNo(), original.name(),
                replacement.id(), replacement.studentNo(), replacement.name(), reason, actorId, actorId);
        return id == null ? 0 : id;
    }

    public void updateReassignment(long id, long termId, LocalDate date, Long sourceSlotId,
                                   LocalTime start, LocalTime end, UserRef original,
                                   UserRef replacement, String reason, long actorId) {
        jdbc.update("""
                UPDATE duty_shift_reassignments
                SET term_id = ?, duty_date = ?, source_slot_id = ?, start_time = ?, end_time = ?,
                    original_user_id = ?, original_student_no_snapshot = ?, original_name_snapshot = ?,
                    replacement_user_id = ?, replacement_student_no_snapshot = ?, replacement_name_snapshot = ?,
                    reason = ?, updated_by = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ?
                """, termId, databaseDate(date), sourceSlotId, databaseTime(start), databaseTime(end),
                original.id(), original.studentNo(), original.name(), replacement.id(), replacement.studentNo(),
                replacement.name(), reason, actorId, id);
    }

    public void deleteReassignment(long id) {
        jdbc.update("DELETE FROM duty_shift_reassignments WHERE id = ?", id);
    }

    public Optional<UserRef> activeUser(String studentNo) {
        return jdbc.query("""
                SELECT id, student_no, name, role FROM users
                WHERE student_no = ? AND status = 'ACTIVE'
                """, (rs, rowNum) -> new UserRef(
                rs.getLong("id"), rs.getString("student_no"), rs.getString("name"), rs.getString("role")
        ), studentNo).stream().findFirst();
    }

    private void replaceExceptionAssignees(long exceptionId, List<ScheduleAssignee> assignees) {
        jdbc.update("DELETE FROM duty_schedule_exception_assignees WHERE exception_id = ?", exceptionId);
        for (int index = 0; index < assignees.size(); index++) {
            ScheduleAssignee assignee = assignees.get(index);
            jdbc.update("""
                    INSERT INTO duty_schedule_exception_assignees (
                      exception_id, user_id, student_no_snapshot, name_snapshot, sort_order
                    ) VALUES (?, ?, ?, ?, ?)
                    """, exceptionId, assignee.userId(), assignee.studentNo(), assignee.name(), index);
        }
    }

    private Map<Long, List<ScheduleAssignee>> slotAssignees(List<Long> ids) {
        return assignees("duty_schedule_assignees", "slot_id", ids);
    }

    private Map<Long, List<ScheduleAssignee>> exceptionAssignees(List<Long> ids) {
        return assignees("duty_schedule_exception_assignees", "exception_id", ids);
    }

    private Map<Long, List<ScheduleAssignee>> assignees(String table, String ownerColumn, List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Map<Long, List<ScheduleAssignee>> result = new LinkedHashMap<>();
        jdbc.query("SELECT " + ownerColumn + ", user_id, student_no_snapshot, name_snapshot, sort_order " +
                "FROM " + table + " WHERE " + ownerColumn + " IN (" + placeholders + ") " +
                "ORDER BY " + ownerColumn + ", sort_order, id", rs -> {
            long ownerId = rs.getLong(ownerColumn);
            result.computeIfAbsent(ownerId, ignored -> new ArrayList<>()).add(new ScheduleAssignee(
                    nullableLong(rs, "user_id"), rs.getString("student_no_snapshot"),
                    rs.getString("name_snapshot"), rs.getInt("sort_order"), false, null
            ));
        }, ids.toArray());
        return result;
    }

    private ShiftReassignment mapReassignment(ResultSet rs, int rowNum) throws SQLException {
        return new ShiftReassignment(
                rs.getLong("id"), rs.getLong("term_id"), localDate(rs, "duty_date"),
                nullableLong(rs, "source_slot_id"), localTime(rs, "start_time"), localTime(rs, "end_time"),
                new ScheduleAssignee(nullableLong(rs, "original_user_id"),
                        rs.getString("original_student_no_snapshot"), rs.getString("original_name_snapshot"), 0, false, null),
                new ScheduleAssignee(nullableLong(rs, "replacement_user_id"),
                        rs.getString("replacement_student_no_snapshot"), rs.getString("replacement_name_snapshot"), 0, true,
                        rs.getString("original_name_snapshot")),
                rs.getString("reason"), rs.getString("created_by_name"), rs.getString("updated_by_name"),
                localDateTime(rs, "created_at"), localDateTime(rs, "updated_at")
        );
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record UserRef(long id, String studentNo, String name, String role) {
        public ScheduleAssignee asAssignee(int order) {
            return new ScheduleAssignee(id, studentNo, name, order, false, null);
        }
    }

    private record SlotRow(long id, LocalTime startTime, LocalTime endTime,
                           String title, String location, String note) {
    }

    private record ExceptionRow(
            long id, long termId, LocalDate date, ScheduleAdjustmentType type, Long sourceSlotId,
            LocalTime startTime, LocalTime endTime, String title, String location, String reason,
            String createdByName, String updatedByName,
            java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt
    ) {
        ScheduleException toDomain(List<ScheduleAssignee> assignees) {
            return new ScheduleException(
                    id, termId, date, type, sourceSlotId, startTime, endTime, title, location,
                    reason, assignees, createdByName, updatedByName, createdAt, updatedAt
            );
        }
    }
}
