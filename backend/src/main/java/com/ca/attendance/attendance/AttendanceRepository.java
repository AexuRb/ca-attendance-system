package com.ca.attendance.attendance;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.ca.attendance.common.JdbcTime.localDate;
import static com.ca.attendance.common.JdbcTime.localDateTime;
import static com.ca.attendance.common.JdbcTime.databaseDate;

@Repository
public class AttendanceRepository {
    private final JdbcTemplate jdbc;

    private final RowMapper<AttendanceRecord> mapper = (rs, rowNum) -> new AttendanceRecord(
            rs.getLong("id"),
            rs.getLong("user_id"),
            com.ca.attendance.common.Role.valueOf(rs.getString("user_role")),
            rs.getString("student_no_snapshot"),
            rs.getString("name_snapshot"),
            localDate(rs, "duty_date"),
            rs.getInt("duty_weekday"),
            rs.getBoolean("is_duty_day"),
            rs.getBoolean("within_duty_period"),
            localDateTime(rs, "check_in_time"),
            localDateTime(rs, "check_out_time"),
            rs.getString("check_in_status"),
            rs.getString("check_out_status"),
            nullableLong(rs, "check_in_reviewed_by"),
            nullableLong(rs, "check_out_reviewed_by"),
            localDateTime(rs, "check_in_reviewed_at"),
            localDateTime(rs, "check_out_reviewed_at"),
            rs.getString("check_in_reject_reason"),
            rs.getString("check_out_reject_reason"),
            rs.getInt("duration_minutes"),
            rs.getInt("valid_hours"),
            rs.getString("effective_status"),
            rs.getString("source"),
            rs.getString("manual_reason")
    );

    public AttendanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AttendanceRecord> findById(long id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT ar.*, u.role AS user_role
                    FROM attendance_records ar
                    JOIN users u ON u.id = ar.user_id
                    WHERE ar.id = ?
                    """, mapper, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<AttendanceRecord> findOpenToday(long userId, LocalDate dutyDate) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT ar.*, u.role AS user_role
                    FROM attendance_records ar
                    JOIN users u ON u.id = ar.user_id
                    WHERE ar.user_id = ?
                      AND ar.duty_date = ?
                      AND ar.check_out_time IS NULL
                      AND ar.check_out_status = 'NOT_SUBMITTED'
                      AND ar.check_in_status <> 'REJECTED'
                    ORDER BY ar.check_in_time DESC
                    LIMIT 1
                    """, mapper, userId, databaseDate(dutyDate)));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<AttendanceRecord> pendingForReviewer(long reviewerId, boolean minister) {
        if (minister) {
            return jdbc.query("""
                    SELECT ar.*, u.role AS user_role
                    FROM attendance_records ar
                    JOIN users u ON u.id = ar.user_id
                    WHERE (ar.check_in_status = 'PENDING' OR ar.check_out_status = 'PENDING')
                      AND ar.user_id <> ?
                    ORDER BY ar.duty_date DESC, ar.check_in_time DESC
                    LIMIT 500
                    """, mapper, reviewerId);
        }
        return jdbc.query("""
                SELECT ar.*, u.role AS user_role
                FROM attendance_records ar
                JOIN users u ON u.id = ar.user_id
                WHERE ar.check_in_status = 'PENDING' OR ar.check_out_status = 'PENDING'
                ORDER BY ar.duty_date DESC, ar.check_in_time DESC
                LIMIT 500
                """, mapper);
    }

    public List<AttendanceRecord> openRecords(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT ar.*, u.role AS user_role
                FROM attendance_records ar
                JOIN users u ON u.id = ar.user_id
                WHERE ar.duty_date BETWEEN ? AND ?
                  AND ar.check_out_time IS NULL
                  AND ar.check_out_status = 'NOT_SUBMITTED'
                  AND ar.check_in_status <> 'REJECTED'
                ORDER BY ar.check_in_time DESC
                LIMIT 500
                """, mapper, databaseDate(from), databaseDate(to));
    }

    public List<AttendanceRecord> search(LocalDate from, LocalDate to, String studentNo, String status) {
        SearchQuery query = searchQuery(from, to, studentNo, status);
        return jdbc.query("""
                SELECT ar.*, u.role AS user_role
                FROM attendance_records ar
                JOIN users u ON u.id = ar.user_id
                """ + query.where() + """

                ORDER BY ar.duty_date DESC, ar.check_in_time DESC
                """, mapper, query.args().toArray());
    }

    public AttendancePage searchPage(LocalDate from, LocalDate to, String studentNo, String status,
                                     int page, int pageSize) {
        SearchQuery query = searchQuery(from, to, studentNo, status);
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM attendance_records ar
                JOIN users u ON u.id = ar.user_id
                """ + query.where(), Long.class, query.args().toArray());
        long totalRows = total == null ? 0 : total;
        int lastPage = Math.max(1, (int) Math.ceil((double) totalRows / pageSize));
        int resolvedPage = Math.min(page, lastPage);
        List<Object> args = new ArrayList<>(query.args());
        args.add(pageSize);
        args.add((resolvedPage - 1) * pageSize);
        List<AttendanceRecord> items = jdbc.query("""
                SELECT ar.*, u.role AS user_role
                FROM attendance_records ar
                JOIN users u ON u.id = ar.user_id
                """ + query.where() + """

                ORDER BY ar.duty_date DESC, ar.check_in_time DESC
                LIMIT ? OFFSET ?
                """, mapper, args.toArray());
        return new AttendancePage(items, totalRows, resolvedPage, pageSize);
    }

    public List<AttendanceRecord> searchForUser(long userId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT ar.*, u.role AS user_role
                FROM attendance_records ar
                JOIN users u ON u.id = ar.user_id
                WHERE ar.user_id = ?
                  AND ar.duty_date BETWEEN ? AND ?
                ORDER BY ar.duty_date DESC, ar.check_in_time DESC
                """, mapper, userId, databaseDate(from), databaseDate(to));
    }

    public long insertCheckIn(long userId, String studentNo, String name, LocalDate dutyDate, int weekday,
                              boolean isDutyDay, boolean withinDutyPeriod, Timestamp checkInTime, String checkInStatus,
                              String effectiveStatus) {
        Long id = jdbc.queryForObject("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday, is_duty_day, within_duty_period,
                  check_in_time, check_in_status, check_out_status, effective_status, source
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'NOT_SUBMITTED', ?, 'PUBLIC')
                RETURNING id
                """, Long.class, userId, studentNo, name, databaseDate(dutyDate), weekday, isDutyDay,
                withinDutyPeriod, checkInTime, checkInStatus, effectiveStatus);
        return id == null ? 0 : id;
    }

    public long insertManual(long userId, String studentNo, String name, LocalDate dutyDate, int weekday,
                             Timestamp checkInTime, Timestamp checkOutTime, String checkInStatus,
                             String checkOutStatus, String reason, long operatorId) {
        Long id = jdbc.queryForObject("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday, is_duty_day, within_duty_period,
                  check_in_time, check_out_time, check_in_status, check_out_status, effective_status,
                  source, manual_reason, created_by, updated_by
                )
                VALUES (?, ?, ?, ?, ?, 1, 1, ?, ?, ?, ?, 'INCOMPLETE', 'ADMIN_MANUAL', ?, ?, ?)
                RETURNING id
                """, Long.class, userId, studentNo, name, databaseDate(dutyDate), weekday, checkInTime, checkOutTime,
                checkInStatus, checkOutStatus, reason, operatorId, operatorId);
        return id == null ? 0 : id;
    }

    public void updateCheckOut(long recordId, Timestamp checkOutTime, String checkOutStatus) {
        jdbc.update("""
                UPDATE attendance_records
                SET check_out_time = ?, check_out_status = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ?
                """, checkOutTime, checkOutStatus, recordId);
    }

    public void updateReview(long recordId, String part, String status, long reviewerId, String reason) {
        if ("CHECK_IN".equals(part)) {
            jdbc.update("""
                    UPDATE attendance_records
                    SET check_in_status = ?, check_in_reviewed_by = ?, check_in_reviewed_at = datetime('now', 'localtime'),
                        check_in_reject_reason = ?, updated_by = ?, updated_at = datetime('now', 'localtime')
                    WHERE id = ?
                    """, status, reviewerId, reason, reviewerId, recordId);
        } else {
            jdbc.update("""
                    UPDATE attendance_records
                    SET check_out_status = ?, check_out_reviewed_by = ?, check_out_reviewed_at = datetime('now', 'localtime'),
                        check_out_reject_reason = ?, updated_by = ?, updated_at = datetime('now', 'localtime')
                    WHERE id = ?
                    """, status, reviewerId, reason, reviewerId, recordId);
        }
    }

    public void updateEffective(long recordId, int durationMinutes, int validHours, String effectiveStatus) {
        jdbc.update("""
                UPDATE attendance_records
                SET duration_minutes = ?, valid_hours = ?, effective_status = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ?
                """, durationMinutes, validHours, effectiveStatus, recordId);
    }

    public void manualUpdate(long id, LocalDate dutyDate, int dutyWeekday, boolean dutyDay,
                             boolean withinDutyPeriod, Timestamp checkInTime, Timestamp checkOutTime,
                             String checkInStatus, String checkOutStatus, String reason, long operatorId) {
        jdbc.update("""
                    UPDATE attendance_records
                    SET duty_date = ?, duty_weekday = ?, is_duty_day = ?, within_duty_period = ?,
                        check_in_time = ?, check_out_time = ?, check_in_status = ?, check_out_status = ?,
                        source = 'ADMIN_MANUAL', manual_reason = ?, updated_by = ?, updated_at = datetime('now', 'localtime')
                    WHERE id = ?
                """, databaseDate(dutyDate), dutyWeekday, dutyDay, withinDutyPeriod,
                checkInTime, checkOutTime, checkInStatus, checkOutStatus, reason, operatorId, id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM attendance_records WHERE id = ?", id);
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private SearchQuery searchQuery(LocalDate from, LocalDate to, String studentNo, String status) {
        String keywordLike = studentNo == null || studentNo.isBlank() ? "%" : "%" + studentNo.trim() + "%";
        String effectiveStatus = status == null || status.isBlank() ? "%" : status;
        return new SearchQuery("""
                WHERE ar.duty_date BETWEEN ? AND ?
                  AND (ar.student_no_snapshot LIKE ? OR ar.name_snapshot LIKE ?)
                  AND ar.effective_status LIKE ?
                """, List.of(
                databaseDate(from),
                databaseDate(to),
                keywordLike,
                keywordLike,
                effectiveStatus
        ));
    }

    public record AttendancePage(List<AttendanceRecord> items, long total, int page, int pageSize) {
    }

    private record SearchQuery(String where, List<Object> args) {
    }
}
