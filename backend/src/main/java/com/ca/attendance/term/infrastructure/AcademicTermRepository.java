package com.ca.attendance.term.infrastructure;

import com.ca.attendance.term.domain.AcademicTerm;
import com.ca.attendance.term.domain.TermStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.ca.attendance.common.JdbcTime.databaseDate;
import static com.ca.attendance.common.JdbcTime.localDate;
import static com.ca.attendance.common.JdbcTime.localDateTime;

@Repository
public class AcademicTermRepository {
    private final JdbcTemplate jdbc;

    private final RowMapper<AcademicTerm> mapper = (rs, rowNum) -> new AcademicTerm(
            rs.getLong("id"),
            rs.getString("term_code"),
            rs.getString("term_name"),
            localDate(rs, "start_date"),
            localDate(rs, "end_date"),
            TermStatus.valueOf(rs.getString("status")),
            rs.getBoolean("legacy"),
            localDateTime(rs, "settling_started_at"),
            localDateTime(rs, "sealed_at"),
            localDateTime(rs, "reopened_at"),
            rs.getString("reopen_reason"),
            localDateTime(rs, "created_at"),
            localDateTime(rs, "updated_at")
    );

    public AcademicTermRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AcademicTerm> list() {
        return jdbc.query("""
                SELECT * FROM academic_terms
                ORDER BY start_date DESC, id DESC
                """, mapper);
    }

    public Optional<AcademicTerm> find(long id) {
        return jdbc.query("SELECT * FROM academic_terms WHERE id = ?", mapper, id)
                .stream().findFirst();
    }

    public Optional<AcademicTerm> active() {
        return jdbc.query("SELECT * FROM academic_terms WHERE status = 'ACTIVE' LIMIT 1", mapper)
                .stream().findFirst();
    }

    public Optional<AcademicTerm> writableForDate(LocalDate date, boolean allowSettling) {
        String statuses = allowSettling ? "('ACTIVE', 'SETTLING')" : "('ACTIVE')";
        return jdbc.query("""
                SELECT * FROM academic_terms
                WHERE status IN """ + statuses + """
                  AND ? BETWEEN start_date AND end_date
                ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END, id DESC
                LIMIT 1
                """, mapper, databaseDate(date)).stream().findFirst();
    }

    public boolean overlaps(LocalDate start, LocalDate end, Long excludedId) {
        Integer count = excludedId == null
                ? jdbc.queryForObject("""
                    SELECT COUNT(*) FROM academic_terms
                    WHERE date(start_date) <= date(?) AND date(end_date) >= date(?)
                    """, Integer.class, databaseDate(end), databaseDate(start))
                : jdbc.queryForObject("""
                    SELECT COUNT(*) FROM academic_terms
                    WHERE id <> ? AND date(start_date) <= date(?) AND date(end_date) >= date(?)
                    """, Integer.class, excludedId, databaseDate(end), databaseDate(start));
        return count != null && count > 0;
    }

    public long create(String code, String name, LocalDate start, LocalDate end, long actorId) {
        Long id = jdbc.queryForObject("""
                INSERT INTO academic_terms (
                  term_code, term_name, start_date, end_date, status, legacy, created_by, updated_by
                ) VALUES (?, ?, ?, ?, 'DRAFT', 0, ?, ?)
                RETURNING id
                """, Long.class, code, name, databaseDate(start), databaseDate(end), actorId, actorId);
        return id == null ? 0 : id;
    }

    public void updateDraft(long id, String code, String name, LocalDate start, LocalDate end, long actorId) {
        jdbc.update("""
                UPDATE academic_terms
                SET term_code = ?, term_name = ?, start_date = ?, end_date = ?, updated_by = ?,
                    updated_at = datetime('now', 'localtime')
                WHERE id = ? AND status = 'DRAFT'
                """, code, name, databaseDate(start), databaseDate(end), actorId, id);
    }

    public void activate(long id, long actorId) {
        jdbc.update("""
                UPDATE academic_terms
                SET status = 'ACTIVE', updated_by = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ?
                """, actorId, id);
    }

    public void beginSettling(long id, long actorId) {
        jdbc.update("""
                UPDATE academic_terms
                SET status = 'SETTLING', settling_started_at = datetime('now', 'localtime'),
                    settling_started_by = ?, updated_by = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ?
                """, actorId, actorId, id);
    }

    public void seal(long id, long actorId) {
        jdbc.update("""
                UPDATE academic_terms
                SET status = 'SEALED', sealed_at = datetime('now', 'localtime'), sealed_by = ?,
                    updated_by = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ?
                """, actorId, actorId, id);
    }

    public void reopen(long id, long actorId, String reason) {
        jdbc.update("""
                UPDATE academic_terms
                SET status = 'SETTLING', reopened_at = datetime('now', 'localtime'), reopened_by = ?,
                    reopen_reason = ?, sealed_at = NULL, sealed_by = NULL,
                    updated_by = ?, updated_at = datetime('now', 'localtime')
                WHERE id = ?
                """, actorId, reason, actorId, id);
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }
}
