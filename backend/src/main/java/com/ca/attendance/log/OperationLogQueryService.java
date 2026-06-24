package com.ca.attendance.log;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class OperationLogQueryService {
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbc;

    private final RowMapper<LogItem> mapper = (rs, rowNum) -> new LogItem(
            rs.getLong("id"),
            nullableLong(rs, "operator_user_id"),
            rs.getString("operator_student_no"),
            rs.getString("operator_name"),
            rs.getString("action_type"),
            rs.getString("target_type"),
            nullableLong(rs, "target_id"),
            rs.getString("before_data_text"),
            rs.getString("after_data_text"),
            rs.getString("reason"),
            rs.getTimestamp("created_at").toLocalDateTime()
    );

    public OperationLogQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public LogPage search(String keyword, String actionType, String from, String to, int page, int pageSize) {
        if (!AuthContext.current().role().canViewOperationLogs()) {
            throw ApiException.forbidden("只有会长或管理员可以查看操作日志");
        }

        int safePage = Math.max(1, page);
        int safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        QueryParts query = buildWhere(keyword, actionType, from, to);

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM operation_logs " + query.where(),
                Long.class,
                query.args().toArray());

        List<Object> dataArgs = new ArrayList<>(query.args());
        dataArgs.add(safePageSize);
        dataArgs.add((safePage - 1) * safePageSize);
        List<LogItem> items = jdbc.query("""
                SELECT id, operator_user_id, operator_student_no, operator_name,
                       action_type, target_type, target_id,
                       CAST(before_data AS CHAR) AS before_data_text,
                       CAST(after_data AS CHAR) AS after_data_text,
                       reason, created_at
                FROM operation_logs
                """ + query.where() + """

                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, mapper, dataArgs.toArray());

        return new LogPage(items, total == null ? 0 : total, safePage, safePageSize);
    }

    private QueryParts buildWhere(String keyword, String actionType, String from, String to) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1");
        List<Object> args = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            where.append("""

                    AND (
                      operator_name LIKE ? OR operator_student_no LIKE ? OR action_type LIKE ?
                      OR target_type LIKE ? OR reason LIKE ?
                    )
                    """);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }

        if (actionType != null && !actionType.isBlank()) {
            where.append("\nAND action_type = ?");
            args.add(actionType.trim());
        }

        LocalDate fromDate = parseDate(from, "开始日期格式不正确");
        if (fromDate != null) {
            where.append("\nAND created_at >= ?");
            args.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }

        LocalDate toDate = parseDate(to, "结束日期格式不正确");
        if (toDate != null) {
            where.append("\nAND created_at < ?");
            args.add(Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
        }

        return new QueryParts(where.toString(), args);
    }

    private LocalDate parseDate(String value, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest(message);
        }
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record QueryParts(String where, List<Object> args) {
    }

    public record LogPage(List<LogItem> items, long total, int page, int pageSize) {
    }

    public record LogItem(
            long id,
            Long operatorUserId,
            String operatorStudentNo,
            String operatorName,
            String actionType,
            String targetType,
            Long targetId,
            String beforeData,
            String afterData,
            String reason,
            LocalDateTime createdAt
    ) {
    }
}
