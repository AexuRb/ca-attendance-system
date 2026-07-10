package com.ca.attendance.attendance;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.ca.attendance.common.JdbcTime.localDate;
import static com.ca.attendance.common.JdbcTime.localDateTime;

@Repository
public class AttendanceRepository {
    private final JdbcTemplate jdbc;

    private final RowMapper<AttendanceRecord> mapper = (rs, rowNum) -> new AttendanceRecord(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("student_no_snapshot"),
            rs.getString("name_snapshot"),
            localDate(rs, "duty_date"),
            rs.getInt("duty_weekday"),
            rs.getBoolean("is_duty_day"),
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
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM attendance_records WHERE id = ?", mapper, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<AttendanceRecord> findOpenToday(long userId, LocalDate dutyDate) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT *
                    FROM attendance_records
                    WHERE user_id = ?
                      AND duty_date = ?
                      AND check_out_time IS NULL
                      AND check_out_status = 'NOT_SUBMITTED'
                      AND check_in_status <> 'REJECTED'
                    ORDER BY check_in_time DESC
                    LIMIT 1
                    """, mapper, userId, dutyDate));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<AttendanceRecord> pendingForReviewer(long reviewerId, boolean minister) {
        if (minister) {
            return jdbc.query("""
                    SELECT *
                    FROM attendance_records
                    WHERE (check_in_status = 'PENDING' OR check_out_status = 'PENDING')
                      AND user_id <> ?
                    ORDER BY duty_date DESC, check_in_time DESC
                    LIMIT 500
                    """, mapper, reviewerId);
        }
        return jdbc.query("""
                SELECT *
                FROM attendance_records
                WHERE check_in_status = 'PENDING' OR check_out_status = 'PENDING'
                ORDER BY duty_date DESC, check_in_time DESC
                LIMIT 500
                """, mapper);
    }

    public List<AttendanceRecord> openRecords(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT *
                FROM attendance_records
                WHERE duty_date BETWEEN ? AND ?
                  AND check_out_time IS NULL
                  AND check_out_status = 'NOT_SUBMITTED'
                  AND check_in_status <> 'REJECTED'
                ORDER BY check_in_time DESC
                LIMIT 500
                """, mapper, from, to);
    }

    public List<AttendanceRecord> search(LocalDate from, LocalDate to, String studentNo, String status) {
        String keywordLike = studentNo == null || studentNo.isBlank() ? "%" : "%" + studentNo.trim() + "%";
        String effectiveStatus = status == null || status.isBlank() ? "%" : status;
        return jdbc.query("""
                SELECT *
                FROM attendance_records
                WHERE duty_date BETWEEN ? AND ?
                  AND (student_no_snapshot LIKE ? OR name_snapshot LIKE ?)
                  AND effective_status LIKE ?
                ORDER BY duty_date DESC, check_in_time DESC
                """, mapper, from, to, keywordLike, keywordLike, effectiveStatus);
    }

    public long insertCheckIn(long userId, String studentNo, String name, LocalDate dutyDate, int weekday,
                              boolean isDutyDay, Timestamp checkInTime, String checkInStatus,
                              String effectiveStatus) {
        Long id = jdbc.queryForObject("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday, is_duty_day,
                  check_in_time, check_in_status, check_out_status, effective_status, source
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'NOT_SUBMITTED', ?, 'PUBLIC')
                RETURNING id
                """, Long.class, userId, studentNo, name, dutyDate, weekday, isDutyDay, checkInTime, checkInStatus, effectiveStatus);
        return id == null ? 0 : id;
    }

    public long insertManual(long userId, String studentNo, String name, LocalDate dutyDate, int weekday,
                             Timestamp checkInTime, Timestamp checkOutTime, String checkInStatus,
                             String checkOutStatus, String reason, long operatorId) {
        Long id = jdbc.queryForObject("""
                INSERT INTO attendance_records (
                  user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday, is_duty_day,
                  check_in_time, check_out_time, check_in_status, check_out_status, effective_status,
                  source, manual_reason, created_by, updated_by
                )
                VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, 'INCOMPLETE', 'ADMIN_MANUAL', ?, ?, ?)
                RETURNING id
                """, Long.class, userId, studentNo, name, dutyDate, weekday, checkInTime, checkOutTime,
                checkInStatus, checkOutStatus, reason, operatorId, operatorId);
        return id == null ? 0 : id;
    }

    public void updateCheckOut(long recordId, Timestamp checkOutTime, String checkOutStatus) {
        jdbc.update("""
                UPDATE attendance_records
                SET check_out_time = ?, check_out_status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, checkOutTime, checkOutStatus, recordId);
    }

    public void updateReview(long recordId, String part, String status, long reviewerId, String reason) {
        if ("CHECK_IN".equals(part)) {
            jdbc.update("""
                    UPDATE attendance_records
                    SET check_in_status = ?, check_in_reviewed_by = ?, check_in_reviewed_at = CURRENT_TIMESTAMP,
                        check_in_reject_reason = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, status, reviewerId, reason, reviewerId, recordId);
        } else {
            jdbc.update("""
                    UPDATE attendance_records
                    SET check_out_status = ?, check_out_reviewed_by = ?, check_out_reviewed_at = CURRENT_TIMESTAMP,
                        check_out_reject_reason = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, status, reviewerId, reason, reviewerId, recordId);
        }
    }

    public void updateEffective(long recordId, int durationMinutes, int validHours, String effectiveStatus) {
        jdbc.update("""
                UPDATE attendance_records
                SET duration_minutes = ?, valid_hours = ?, effective_status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, durationMinutes, validHours, effectiveStatus, recordId);
    }

    public void manualUpdate(long id, Timestamp checkInTime, Timestamp checkOutTime, String checkInStatus,
                             String checkOutStatus, String reason, long operatorId) {
        jdbc.update("""
                UPDATE attendance_records
                SET check_in_time = ?, check_out_time = ?, check_in_status = ?, check_out_status = ?,
                    source = 'ADMIN_MANUAL', manual_reason = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, checkInTime, checkOutTime, checkInStatus, checkOutStatus, reason, operatorId, id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM attendance_records WHERE id = ?", id);
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }
}
