package com.ca.attendance.log;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.maintenance.BackupService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
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
    private final BackupService backups;

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

    public OperationLogQueryService(JdbcTemplate jdbc, BackupService backups) {
        this.jdbc = jdbc;
        this.backups = backups;
    }

    public LogPage search(String keyword, String actionType, String from, String to, int page, int pageSize) {
        if (!AuthContext.current().role().canViewOperationLogs()) {
            throw ApiException.forbidden("只有管理员可以查看操作日志");
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

    public ClearResult clear() {
        if (!AuthContext.current().role().canViewOperationLogs()) {
            throw ApiException.forbidden("只有管理员可以清空操作日志");
        }
        BackupService.BackupItem safetyBackup = backups.create();
        int deleted = jdbc.update("DELETE FROM operation_logs");
        return new ClearResult(deleted, safetyBackup);
    }

    public byte[] export(String keyword, String actionType, String from, String to) {
        if (!AuthContext.current().role().canViewOperationLogs()) {
            throw ApiException.forbidden("只有管理员可以导出操作日志");
        }
        QueryParts query = buildWhere(keyword, actionType, from, to);
        List<LogItem> items = jdbc.query("""
                SELECT id, operator_user_id, operator_student_no, operator_name,
                       action_type, target_type, target_id,
                       CAST(before_data AS CHAR) AS before_data_text,
                       CAST(after_data AS CHAR) AS after_data_text,
                       reason, created_at
                FROM operation_logs
                """ + query.where() + """

                ORDER BY created_at DESC, id DESC
                """, mapper, query.args().toArray());

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("操作日志");
            CellStyle headerStyle = headerStyle(wb);
            CellStyle textStyle = textStyle(wb);
            String[] headers = {"时间", "操作人", "操作", "对象", "原因", "修改前", "修改后"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < items.size(); i++) {
                LogItem item = items.get(i);
                Row row = sheet.createRow(i + 1);
                writeCell(row, 0, item.createdAt().toString().replace('T', ' '), textStyle);
                writeCell(row, 1, operatorText(item), textStyle);
                writeCell(row, 2, item.actionType(), textStyle);
                writeCell(row, 3, targetText(item), textStyle);
                writeCell(row, 4, item.reason(), textStyle);
                writeCell(row, 5, item.beforeData(), textStyle);
                writeCell(row, 6, item.afterData(), textStyle);
            }

            sheet.createFreezePane(0, 1);
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(Math.max(sheet.getColumnWidth(i) + 512, 12 * 256), 50 * 256));
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw ApiException.badRequest("导出操作日志失败");
        }
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

    private String operatorText(LogItem item) {
        if (item.operatorName() == null && item.operatorStudentNo() == null) {
            return "-";
        }
        return item.operatorStudentNo() == null
                ? item.operatorName()
                : (item.operatorName() == null ? "-" : item.operatorName()) + "（" + item.operatorStudentNo() + "）";
    }

    private String targetText(LogItem item) {
        return item.targetId() == null ? item.targetType() : item.targetType() + "#" + item.targetId();
    }

    private void writeCell(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = borderedStyle(wb);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = wb.createFont();
        font.setFontName("Microsoft YaHei");
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle textStyle(Workbook wb) {
        CellStyle style = borderedStyle(wb);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        Font font = wb.createFont();
        font.setFontName("Microsoft YaHei");
        style.setFont(font);
        style.setWrapText(true);
        return style;
    }

    private CellStyle borderedStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    private record QueryParts(String where, List<Object> args) {
    }

    public record LogPage(List<LogItem> items, long total, int page, int pageSize) {
    }

    public record ClearResult(int deleted, BackupService.BackupItem safetyBackup) {
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
