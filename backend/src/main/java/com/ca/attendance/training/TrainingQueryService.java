package com.ca.attendance.training;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.ExportRowLimit;
import com.ca.attendance.common.PaginationPolicy;
import com.ca.attendance.common.SqlLike;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ca.attendance.common.JdbcTime.databaseDate;
import static com.ca.attendance.common.JdbcTime.localDate;
import static com.ca.attendance.common.JdbcTime.localDateTime;
import static com.ca.attendance.common.JdbcTime.localTime;

@Service
public class TrainingQueryService {
    private final JdbcTemplate jdbc;

    private final RowMapper<TrainingSessionItem> sessionMapper = (rs, rowNum) -> new TrainingSessionItem(
            rs.getLong("id"),
            rs.getString("title"),
            localDate(rs, "training_date"),
            localTime(rs, "start_time"),
            localTime(rs, "end_time"),
            rs.getString("location"),
            rs.getString("speaker"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getLong("participant_count"),
            rs.getBigDecimal("total_duration_hours"),
            rs.getString("created_by_name"),
            rs.getString("updated_by_name"),
            localDateTime(rs, "created_at"),
            localDateTime(rs, "updated_at")
    );

    private final RowMapper<TrainingParticipantItem> participantMapper = (rs, rowNum) -> new TrainingParticipantItem(
            rs.getLong("id"),
            rs.getLong("session_id"),
            nullableLong(rs, "user_id"),
            rs.getString("student_no_snapshot"),
            rs.getString("name_snapshot"),
            rs.getBigDecimal("duration_hours"),
            rs.getString("remark"),
            rs.getString("created_by_name"),
            rs.getString("updated_by_name"),
            localDateTime(rs, "created_at"),
            localDateTime(rs, "updated_at")
    );

    public TrainingQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    PageResult<TrainingSessionItem> sessionPage(
            String keyword,
            String status,
            LocalDate from,
            LocalDate to,
            int page,
            int pageSize
    ) {
        PaginationPolicy.PageRequest paging = PaginationPolicy.normalize(page, pageSize);
        SessionQuery query = sessionQuery(keyword, status, from, to);
        Long totalValue = jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_sessions s " + query.where(),
                Long.class,
                query.args().toArray()
        );
        long total = totalValue == null ? 0 : totalValue;
        int resolvedPage = PaginationPolicy.resolvePage(paging.page(), total, paging.pageSize());
        List<TrainingSessionItem> items = querySessions(
                query.where(),
                query.args(),
                paging.pageSize(),
                (long) (resolvedPage - 1) * paging.pageSize()
        );
        return new PageResult<>(
                items,
                total,
                resolvedPage,
                paging.pageSize(),
                (long) resolvedPage * paging.pageSize() < total
        );
    }

    PageResult<TrainingParticipantItem> participantPage(
            long sessionId,
            String keyword,
            int page,
            int pageSize
    ) {
        PaginationPolicy.PageRequest paging = PaginationPolicy.normalize(page, pageSize);
        ParticipantQuery query = participantQuery(sessionId, keyword);
        Long totalValue = jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_participants p " + query.where(),
                Long.class,
                query.args().toArray()
        );
        long total = totalValue == null ? 0 : totalValue;
        int resolvedPage = PaginationPolicy.resolvePage(paging.page(), total, paging.pageSize());
        List<TrainingParticipantItem> items = queryParticipants(
                sessionId,
                keyword,
                paging.pageSize(),
                (long) (resolvedPage - 1) * paging.pageSize()
        );
        return new PageResult<>(
                items,
                total,
                resolvedPage,
                paging.pageSize(),
                (long) resolvedPage * paging.pageSize() < total
        );
    }

    List<TrainingSessionItem> sessions(String keyword, String status, LocalDate from, LocalDate to) {
        SessionQuery query = sessionQuery(keyword, status, from, to);
        return querySessions(query.where(), query.args(), ExportRowLimit.FETCH_LIMIT, 0L);
    }

    List<TrainingParticipantItem> participants(long sessionId) {
        return queryParticipants(sessionId, null, ExportRowLimit.FETCH_LIMIT, 0L);
    }

    Optional<TrainingSessionItem> findSession(long id) {
        return querySessions("WHERE s.id = ?", id).stream().findFirst();
    }

    Optional<TrainingParticipantItem> findParticipant(long sessionId, long participantId) {
        return jdbc.query("""
                SELECT p.*,
                       cb.name AS created_by_name,
                       ub.name AS updated_by_name
                FROM training_participants p
                LEFT JOIN users cb ON cb.id = p.created_by
                LEFT JOIN users ub ON ub.id = p.updated_by
                WHERE p.session_id = ? AND p.id = ?
                """, participantMapper, sessionId, participantId).stream().findFirst();
    }

    List<MyTrainingRecordItem> myRecords(long userId, LocalDate from, LocalDate to) {
        DateRange range = personalDateRange(from, to);
        return jdbc.query("""
                SELECT
                  p.id AS participant_id,
                  s.id AS session_id,
                  s.title,
                  s.training_date,
                  s.start_time,
                  s.end_time,
                  s.location,
                  s.speaker,
                  p.duration_hours,
                  p.remark
                FROM training_participants p
                JOIN training_sessions s ON s.id = p.session_id
                WHERE p.user_id = ?
                  AND s.status <> 'ARCHIVED'
                  AND s.training_date BETWEEN ? AND ?
                ORDER BY s.training_date DESC, s.start_time DESC, p.id DESC
                """, (rs, rowNum) -> new MyTrainingRecordItem(
                rs.getLong("participant_id"),
                rs.getLong("session_id"),
                rs.getString("title"),
                localDate(rs, "training_date"),
                localTime(rs, "start_time"),
                localTime(rs, "end_time"),
                rs.getString("location"),
                rs.getString("speaker"),
                rs.getBigDecimal("duration_hours"),
                rs.getString("remark")
        ), userId, databaseDate(range.start()), databaseDate(range.end()));
    }

    List<Map<String, Object>> memberSummary(LocalDate from, LocalDate to) {
        return jdbc.queryForList("""
                SELECT p.student_no_snapshot AS studentNo,
                       p.name_snapshot AS name,
                       COUNT(*) AS trainingCount,
                       COALESCE(SUM(p.duration_hours), 0) AS durationHours
                FROM training_participants p
                JOIN training_sessions s ON s.id = p.session_id
                WHERE s.status <> 'ARCHIVED'
                  AND s.training_date BETWEEN ? AND ?
                  AND p.duration_hours > 0
                GROUP BY p.student_no_snapshot, p.name_snapshot
                ORDER BY durationHours DESC, trainingCount DESC, p.student_no_snapshot
                LIMIT ?
                """, from, to, ExportRowLimit.FETCH_LIMIT);
    }

    private SessionQuery sessionQuery(String keyword, String status, LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.of(LocalDate.now().getYear(), 1, 1) : from;
        LocalDate end = to == null ? LocalDate.now().plusYears(1) : to;
        if (start.isAfter(end)) {
            throw ApiException.badRequest("开始日期不能晚于结束日期");
        }
        List<Object> args = new ArrayList<>();
        args.add(databaseDate(start));
        args.add(databaseDate(end));
        StringBuilder where = new StringBuilder("""
                WHERE s.status <> 'ARCHIVED'
                  AND s.training_date BETWEEN ? AND ?
                """);
        if (keyword != null && !keyword.isBlank()) {
            where.append("""
                    AND (
                      s.title LIKE ? ESCAPE '\\'
                      OR s.location LIKE ? ESCAPE '\\'
                      OR s.speaker LIKE ? ESCAPE '\\'
                      OR s.description LIKE ? ESCAPE '\\'
                    )
                    """);
            String like = SqlLike.contains(keyword);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            String normalizedStatus = TrainingSessionStatus.parse(status);
            if ("ARCHIVED".equals(normalizedStatus)) {
                throw ApiException.badRequest("培训列表不支持查询已归档场次");
            }
            where.append(" AND s.status = ?");
            args.add(normalizedStatus);
        }
        return new SessionQuery(where.toString(), args);
    }

    private List<TrainingSessionItem> querySessions(String where, Object... args) {
        return querySessions(where, Arrays.asList(args), null, null);
    }

    private List<TrainingSessionItem> querySessions(
            String where,
            List<Object> args,
            Integer limit,
            Long offset
    ) {
        List<Object> queryArgs = new ArrayList<>(args);
        String pagination = "";
        if (limit != null && offset != null) {
            pagination = "\nLIMIT ? OFFSET ?";
            queryArgs.add(limit);
            queryArgs.add(offset);
        }
        return jdbc.query("""
                SELECT s.*,
                       cb.name AS created_by_name,
                       ub.name AS updated_by_name,
                       (SELECT COUNT(*) FROM training_participants p WHERE p.session_id = s.id AND p.duration_hours > 0) AS participant_count,
                       (SELECT COALESCE(SUM(p.duration_hours), 0) FROM training_participants p WHERE p.session_id = s.id) AS total_duration_hours
                FROM training_sessions s
                LEFT JOIN users cb ON cb.id = s.created_by
                LEFT JOIN users ub ON ub.id = s.updated_by
                """ + where + """

                ORDER BY s.training_date DESC, s.id DESC
                """ + pagination, sessionMapper, queryArgs.toArray());
    }

    private List<TrainingParticipantItem> queryParticipants(
            long sessionId,
            String keyword,
            Integer limit,
            Long offset
    ) {
        ParticipantQuery query = participantQuery(sessionId, keyword);
        List<Object> queryArgs = new ArrayList<>(query.args());
        String pagination = "";
        if (limit != null && offset != null) {
            pagination = "\nLIMIT ? OFFSET ?";
            queryArgs.add(limit);
            queryArgs.add(offset);
        }
        return jdbc.query("""
                SELECT p.*,
                       cb.name AS created_by_name,
                       ub.name AS updated_by_name
                FROM training_participants p
                JOIN training_sessions s ON s.id = p.session_id
                LEFT JOIN users cb ON cb.id = p.created_by
                LEFT JOIN users ub ON ub.id = p.updated_by
                """ + query.where() + """

                ORDER BY
                  CASE WHEN s.speaker IS NOT NULL AND s.speaker <> '' AND p.name_snapshot = s.speaker THEN 0 ELSE 1 END,
                  p.student_no_snapshot,
                  p.id
                """ + pagination, participantMapper, queryArgs.toArray());
    }

    private ParticipantQuery participantQuery(long sessionId, String keyword) {
        StringBuilder where = new StringBuilder("WHERE p.session_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(sessionId);
        if (keyword != null && !keyword.isBlank()) {
            where.append("""
                    AND (
                      p.student_no_snapshot LIKE ? ESCAPE '\\'
                      OR p.name_snapshot LIKE ? ESCAPE '\\'
                      OR p.remark LIKE ? ESCAPE '\\'
                    )
                    """);
            String like = SqlLike.contains(keyword);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return new ParticipantQuery(where.toString(), args);
    }

    private DateRange personalDateRange(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.of(LocalDate.now().getYear(), 1, 1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        if (start.isAfter(end)) {
            throw ApiException.badRequest("开始日期不能晚于结束日期");
        }
        return new DateRange(start, end);
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        if (value != null) {
            try {
                return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException ignored) {
                // Fall through to zero for an unexpected database value.
            }
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    record PageResult<T>(List<T> items, long total, int page, int pageSize, boolean hasMore) {
    }

    private record SessionQuery(String where, List<Object> args) {
    }

    private record ParticipantQuery(String where, List<Object> args) {
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }
}
