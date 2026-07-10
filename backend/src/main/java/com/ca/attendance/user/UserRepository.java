package com.ca.attendance.user;

import com.ca.attendance.common.Role;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.ca.attendance.common.JdbcTime.localDateTime;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;

    private final RowMapper<UserSummary> summaryMapper = (rs, rowNum) -> new UserSummary(
            rs.getLong("id"),
            rs.getString("student_no"),
            rs.getString("name"),
            Role.valueOf(rs.getString("role")),
            rs.getString("status"),
            rs.getString("phone"),
            rs.getString("major"),
            rs.getString("grade"),
            rs.getString("qq"),
            rs.getBoolean("must_change_password"),
            localDateTime(rs, "created_at"),
            localDateTime(rs, "updated_at")
    );

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserLoginRow> findLoginByStudentNo(String studentNo) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT id, student_no, name, password_hash, role, status, must_change_password
                    FROM users
                    WHERE student_no = ?
                    """, (rs, rowNum) -> new UserLoginRow(
                    rs.getLong("id"),
                    rs.getString("student_no"),
                    rs.getString("name"),
                    rs.getString("password_hash"),
                    Role.valueOf(rs.getString("role")),
                    rs.getString("status"),
                    rs.getBoolean("must_change_password")
            ), studentNo));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<UserSummary> findSummaryById(long id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM users WHERE id = ?", summaryMapper, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<UserSummary> findActiveByStudentNo(String studentNo) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM users WHERE student_no = ? AND status = 'ACTIVE'", summaryMapper, studentNo));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<UserSummary> findActiveByName(String name) {
        return jdbc.query("""
                SELECT *
                FROM users
                WHERE name = ? AND status = 'ACTIVE'
                ORDER BY student_no
                LIMIT 50
                """, summaryMapper, name == null ? "" : name.trim());
    }

    public List<UserSummary> search(String keyword, String role, String status, String grade) {
        SearchQuery query = searchQuery(keyword, role, status, grade);
        return jdbc.query("""
                SELECT *
                FROM users
                """ + query.where() + """

                ORDER BY role, student_no
                LIMIT 1000
                """, summaryMapper, query.args().toArray());
    }

    public UserPage searchPage(String keyword, String role, String status, String grade, int page, int pageSize) {
        SearchQuery query = searchQuery(keyword, role, status, grade);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM users " + query.where(), Long.class, query.args().toArray());
        List<Object> args = new ArrayList<>(query.args());
        args.add(pageSize);
        args.add((page - 1) * pageSize);
        List<UserSummary> items = jdbc.query("""
                SELECT *
                FROM users
                """ + query.where() + """

                ORDER BY role, student_no
                LIMIT ? OFFSET ?
                """, summaryMapper, args.toArray());
        return new UserPage(items, total == null ? 0 : total, page, pageSize);
    }

    public List<Long> searchIds(String keyword, String role, String status, String grade) {
        SearchQuery query = searchQuery(keyword, role, status, grade);
        return jdbc.queryForList("""
                SELECT id
                FROM users
                """ + query.where() + """

                ORDER BY role, student_no
                """, Long.class, query.args().toArray());
    }

    public List<String> grades() {
        return jdbc.queryForList("""
                SELECT DISTINCT grade
                FROM users
                WHERE grade IS NOT NULL AND grade <> ''
                ORDER BY grade DESC
                """, String.class);
    }

    public record UserLoginRow(
            long id,
            String studentNo,
            String name,
            String passwordHash,
            Role role,
            String status,
            boolean mustChangePassword
    ) {
    }

    public record UserPage(List<UserSummary> items, long total, int page, int pageSize) {
    }

    private SearchQuery searchQuery(String keyword, String role, String status, String grade) {
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        String roleFilter = role == null || role.isBlank() ? "%" : role;
        String statusFilter = status == null || status.isBlank() ? "%" : status;
        String gradeFilter = grade == null || grade.isBlank() ? "%" : grade.trim();
        return new SearchQuery("""
                WHERE (student_no LIKE ? OR name LIKE ? OR phone LIKE ? OR major LIKE ?)
                  AND role LIKE ?
                  AND status LIKE ?
                  AND COALESCE(grade, '') LIKE ?
                """, List.of(like, like, like, like, roleFilter, statusFilter, gradeFilter));
    }

    private record SearchQuery(String where, List<Object> args) {
    }
}
