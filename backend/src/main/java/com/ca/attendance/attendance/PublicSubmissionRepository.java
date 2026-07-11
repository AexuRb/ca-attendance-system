package com.ca.attendance.attendance;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import static com.ca.attendance.common.JdbcTime.localDateTime;

@Repository
public class PublicSubmissionRepository {
    private final JdbcTemplate jdbc;

    public PublicSubmissionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Receipt> findByRequestId(String requestId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT request_id, student_no, record_id, action, name, submitted_at, review_status, message
                    FROM public_attendance_submissions
                    WHERE request_id = ?
                    """, (rs, rowNum) -> new Receipt(
                    rs.getString("request_id"),
                    rs.getString("student_no"),
                    rs.getLong("record_id"),
                    rs.getString("action"),
                    rs.getString("name"),
                    localDateTime(rs, "submitted_at"),
                    rs.getString("review_status"),
                    rs.getString("message")
            ), requestId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public void save(Receipt receipt) {
        jdbc.update("""
                INSERT INTO public_attendance_submissions (
                  request_id, student_no, record_id, action, name, submitted_at, review_status, message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                receipt.requestId(),
                receipt.studentNo(),
                receipt.recordId(),
                receipt.action(),
                receipt.name(),
                Timestamp.valueOf(receipt.submittedAt()),
                receipt.reviewStatus(),
                receipt.message()
        );
    }

    public record Receipt(
            String requestId,
            String studentNo,
            long recordId,
            String action,
            String name,
            LocalDateTime submittedAt,
            String reviewStatus,
            String message
    ) {
    }
}
