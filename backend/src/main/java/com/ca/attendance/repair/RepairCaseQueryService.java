package com.ca.attendance.repair;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.ExportRowLimit;
import com.ca.attendance.common.PaginationPolicy;
import com.ca.attendance.common.SqlLike;
import com.ca.attendance.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.ca.attendance.common.JdbcTime.localDateTime;

@Service
public class RepairCaseQueryService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;

    private final RowMapper<RepairCaseItem> mapper = (rs, rowNum) -> new RepairCaseItem(
            rs.getLong("id"),
            rs.getString("case_no"),
            rs.getString("agreement_type"),
            rs.getString("owner_name"),
            rs.getString("owner_phone"),
            rs.getString("owner_org"),
            rs.getString("device_type"),
            rs.getString("device_brand"),
            rs.getString("device_model"),
            rs.getString("device_serial"),
            rs.getString("accessories"),
            rs.getString("fault_description"),
            rs.getString("service_description"),
            rs.getBoolean("data_backup_confirmed"),
            rs.getBoolean("risk_acknowledged"),
            rs.getBoolean("privacy_acknowledged"),
            RepairStatus.normalizeStored(rs.getString("status")),
            localDateTime(rs, "received_at"),
            localDateTime(rs, "completed_at"),
            nullableLong(rs, "handler_user_id"),
            rs.getString("handler_name_snapshot"),
            rs.getString("remark"),
            rs.getString("created_by_name"),
            rs.getString("updated_by_name"),
            rs.getString("deleted_by_name"),
            localDateTime(rs, "created_at"),
            localDateTime(rs, "updated_at"),
            localDateTime(rs, "deleted_at")
    );

    public RepairCaseQueryService(JdbcTemplate jdbc, UserRepository users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    List<RepairCaseItem> list(String keyword, String status, LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.of(LocalDate.now().getYear(), 1, 1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        RepairQuery query = buildQuery(keyword, status, start, end, true);
        return queryCases(query.where(), query.args(), ExportRowLimit.FETCH_LIMIT, 0);
    }

    PageResult<RepairCaseItem> page(
            String keyword,
            String status,
            LocalDate from,
            LocalDate to,
            int page,
            int pageSize
    ) {
        LocalDate start = from == null ? LocalDate.of(LocalDate.now().getYear(), 1, 1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            throw ApiException.badRequest("分页查询必须指定维修状态");
        }
        PaginationPolicy.PageRequest paging = PaginationPolicy.normalize(page, pageSize);
        String normalizedStatus = RepairStatus.parse(status);
        RepairQuery query = buildQuery(keyword, normalizedStatus, start, end, false);
        Map<String, Long> statusCounts = statusCounts(keyword, start, end);
        long total = statusCounts.getOrDefault(normalizedStatus, 0L);
        int resolvedPage = PaginationPolicy.resolvePage(paging.page(), total, paging.pageSize());
        List<RepairCaseItem> items = queryCases(
                query.where(),
                query.args(),
                paging.pageSize(),
                (resolvedPage - 1) * paging.pageSize()
        );
        return new PageResult<>(
                items,
                total,
                resolvedPage,
                paging.pageSize(),
                (long) resolvedPage * paging.pageSize() < total,
                statusCounts
        );
    }

    List<UserRepository.UserCandidate> handlerCandidates(String keyword) {
        return users.searchActiveCandidates(keyword, 1000);
    }

    List<RepairCaseItem> recycleBin() {
        return jdbc.query("""
                SELECT r.*,
                       cb.name AS created_by_name,
                       ub.name AS updated_by_name,
                       db.name AS deleted_by_name
                FROM repair_cases r
                LEFT JOIN users cb ON cb.id = r.created_by
                LEFT JOIN users ub ON ub.id = r.updated_by
                LEFT JOIN users db ON db.id = r.deleted_by
                WHERE r.deleted_at IS NOT NULL
                ORDER BY r.deleted_at DESC, r.id DESC
                """, mapper);
    }

    Optional<RepairCaseItem> findCase(long id) {
        return queryCases("WHERE r.id = ? AND r.deleted_at IS NULL", id).stream().findFirst();
    }

    Optional<RepairCaseItem> findDeletedCase(long id) {
        return queryCases("WHERE r.id = ? AND r.deleted_at IS NOT NULL", id).stream().findFirst();
    }

    private Map<String, Long> statusCounts(String keyword, LocalDate start, LocalDate end) {
        RepairQuery query = buildQuery(keyword, "ALL", start, end, true);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("REPAIRING", 0L);
        counts.put("COMPLETED", 0L);
        counts.put("CANCELED", 0L);
        jdbc.query("""
                        SELECT CASE
                                 WHEN r.status IN ('RECEIVED', 'DIAGNOSING', 'REPAIRING', 'WAITING_PICKUP')
                                   THEN 'REPAIRING'
                                 ELSE r.status
                               END AS status_group,
                               COUNT(*) AS total
                        FROM repair_cases r
                        """ + query.where() + """
                        GROUP BY status_group
                        """,
                rs -> {
                    String status = rs.getString("status_group");
                    if (counts.containsKey(status)) {
                        counts.put(status, rs.getLong("total"));
                    }
                },
                query.args().toArray());
        return counts;
    }

    private List<RepairCaseItem> queryCases(String where, Object... args) {
        return queryCases(where, List.of(args), null, null);
    }

    private List<RepairCaseItem> queryCases(
            String where,
            List<Object> args,
            Integer limit,
            Integer offset
    ) {
        List<Object> queryArgs = new ArrayList<>(args);
        String pagination = "";
        if (limit != null && offset != null) {
            pagination = "\nLIMIT ? OFFSET ?";
            queryArgs.add(limit);
            queryArgs.add(offset);
        }
        return jdbc.query("""
                SELECT r.*,
                       cb.name AS created_by_name,
                       ub.name AS updated_by_name,
                       db.name AS deleted_by_name
                FROM repair_cases r
                LEFT JOIN users cb ON cb.id = r.created_by
                LEFT JOIN users ub ON ub.id = r.updated_by
                LEFT JOIN users db ON db.id = r.deleted_by
                """ + where + """

                ORDER BY
                  CASE r.status
                    WHEN 'REPAIRING' THEN 1
                    WHEN 'RECEIVED' THEN 1
                    WHEN 'DIAGNOSING' THEN 1
                    WHEN 'WAITING_PICKUP' THEN 1
                    WHEN 'COMPLETED' THEN 2
                    WHEN 'CANCELED' THEN 3
                    ELSE 9
                  END,
                  r.received_at DESC,
                  r.id DESC
                """ + pagination, mapper, queryArgs.toArray());
    }

    private RepairQuery buildQuery(
            String keyword,
            String status,
            LocalDate start,
            LocalDate end,
            boolean allowAllStatuses
    ) {
        if (start.isAfter(end)) {
            throw ApiException.badRequest("开始日期不能晚于结束日期");
        }
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(start.atStartOfDay()));
        args.add(Timestamp.valueOf(end.plusDays(1).atStartOfDay()));
        StringBuilder where = new StringBuilder("""
                WHERE r.deleted_at IS NULL
                  AND r.received_at >= ?
                  AND r.received_at < ?
                """);

        String normalizedStatus = status == null || status.isBlank()
                ? ""
                : status.trim().toUpperCase(Locale.ROOT);
        if (normalizedStatus.isBlank()) {
            appendInProgressFilter(where);
        } else if ("ALL".equals(normalizedStatus)) {
            if (!allowAllStatuses) {
                throw ApiException.badRequest("分页查询必须指定维修状态");
            }
        } else {
            String parsedStatus = RepairStatus.parse(normalizedStatus);
            if ("REPAIRING".equals(parsedStatus)) {
                appendInProgressFilter(where);
            } else {
                where.append("AND r.status = ?\n");
                args.add(parsedStatus);
            }
        }

        if (keyword != null && !keyword.isBlank()) {
            where.append("""
                    AND (
                      r.case_no LIKE ? ESCAPE '\\'
                      OR r.owner_name LIKE ? ESCAPE '\\'
                      OR r.owner_phone LIKE ? ESCAPE '\\'
                      OR r.device_type LIKE ? ESCAPE '\\'
                      OR r.device_brand LIKE ? ESCAPE '\\'
                      OR r.device_model LIKE ? ESCAPE '\\'
                      OR r.fault_description LIKE ? ESCAPE '\\'
                      OR r.service_description LIKE ? ESCAPE '\\'
                      OR r.handler_name_snapshot LIKE ? ESCAPE '\\'
                    )
                    """);
            String like = SqlLike.contains(keyword);
            for (int i = 0; i < 9; i++) {
                args.add(like);
            }
        }
        return new RepairQuery(where.toString(), args);
    }

    private void appendInProgressFilter(StringBuilder where) {
        where.append("AND r.status IN ('RECEIVED', 'DIAGNOSING', 'REPAIRING', 'WAITING_PICKUP')\n");
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    record PageResult<T>(
            List<T> items,
            long total,
            int page,
            int pageSize,
            boolean hasMore,
            Map<String, Long> statusCounts
    ) {
    }

    private record RepairQuery(String where, List<Object> args) {
    }
}
