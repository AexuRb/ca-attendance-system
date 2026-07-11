package com.ca.attendance.export;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CustomExportService {
    private static final int MAX_ROWS = 50_000;
    private static final int PREVIEW_ROWS = 12;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<SourceDefinition> SOURCES = List.of(
            membersSource(),
            attendanceSource(),
            trainingSource(),
            scheduleSource(),
            repairSource(),
            logsSource()
    );

    private final JdbcTemplate jdbc;
    private final OperationLogService logs;

    public CustomExportService(JdbcTemplate jdbc, OperationLogService logs) {
        this.jdbc = jdbc;
        this.logs = logs;
    }

    public ExportOptions options() {
        AuthUser current = AuthContext.current();
        requireExporter(current);
        List<SourceOption> sources = SOURCES.stream()
                .filter(source -> !source.adminOnly() || current.role() == Role.ADMIN)
                .map(source -> new SourceOption(source.id(), source.label(), source.fields(), source.filters()))
                .toList();
        return new ExportOptions(sources);
    }

    public ExportFile export(ExportRequest request) {
        AuthUser current = AuthContext.current();
        requireExporter(current);
        PreparedExport prepared = prepare(request, current);
        List<Map<String, Object>> rows = queryRows(prepared);

        String filename = filename(request.filename(), prepared.source().label());
        byte[] bytes = workbookBytes(workbook -> writeWorkbook(
                workbook,
                prepared.source(),
                prepared.fields(),
                prepared.filters(),
                rows
        ));
        logs.log(
                "EXPORT_CUSTOM_DATA",
                "custom_exports",
                null,
                null,
                Map.of(
                        "source", prepared.source().id(),
                        "fields", prepared.fields().stream().map(FieldOption::id).toList(),
                        "filters", prepared.filters(),
                        "rows", rows.size(),
                        "filename", filename
                ),
                "自定义导出 " + prepared.source().label()
        );
        return new ExportFile(filename, bytes, rows.size());
    }

    public ExportPreview preview(ExportRequest request) {
        AuthUser current = AuthContext.current();
        requireExporter(current);
        PreparedExport prepared = prepare(request, current);
        List<Map<String, Object>> rows = queryRows(prepared);
        List<Map<String, Object>> previewRows = rows.stream()
                .limit(PREVIEW_ROWS)
                .map(row -> previewRow(row, prepared.fields()))
                .toList();

        return new ExportPreview(
                prepared.source().id(),
                prepared.source().label(),
                prepared.fields(),
                prepared.filters(),
                rows.size(),
                rows.size() > previewRows.size(),
                previewRows
        );
    }

    private PreparedExport prepare(ExportRequest request, AuthUser current) {
        if (request == null) {
            throw ApiException.badRequest("导出配置不能为空");
        }
        SourceDefinition source = SOURCES.stream()
                .filter(item -> item.id().equalsIgnoreCase(text(request.source())))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("导出数据源不存在"));
        if (source.adminOnly() && current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有管理员可以导出操作日志");
        }

        List<FieldOption> selectedFields = validateFields(source, request.fields());
        Map<String, String> filters = validateFilters(source, request.filters());
        return new PreparedExport(source, selectedFields, filters);
    }

    private List<Map<String, Object>> queryRows(PreparedExport prepared) {
        List<Map<String, Object>> rows = query(prepared.source().id(), prepared.filters());
        if (rows.size() > MAX_ROWS) {
            throw ApiException.badRequest("导出结果超过 " + MAX_ROWS + " 行，请缩小筛选范围");
        }
        return rows;
    }

    private Map<String, Object> previewRow(Map<String, Object> row, List<FieldOption> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        fields.forEach(field -> result.put(field.id(), row.get(field.id())));
        return result;
    }

    private List<FieldOption> validateFields(SourceDefinition source, List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw ApiException.badRequest("请至少选择一个导出字段");
        }
        Map<String, FieldOption> allowed = new LinkedHashMap<>();
        source.fields().forEach(field -> allowed.put(field.id(), field));
        Set<String> seen = new HashSet<>();
        List<FieldOption> result = new ArrayList<>();
        for (String value : requested) {
            String id = text(value);
            FieldOption field = allowed.get(id);
            if (field == null) {
                throw ApiException.badRequest("导出字段不合法：" + id);
            }
            if (!seen.add(id)) {
                throw ApiException.badRequest("导出字段不能重复：" + field.label());
            }
            result.add(field);
        }
        return List.copyOf(result);
    }

    private Map<String, String> validateFilters(SourceDefinition source, Map<String, String> requested) {
        Map<String, FilterOption> allowed = new LinkedHashMap<>();
        source.filters().forEach(filter -> allowed.put(filter.id(), filter));
        Map<String, String> result = new LinkedHashMap<>();
        if (requested == null) return result;
        for (Map.Entry<String, String> entry : requested.entrySet()) {
            FilterOption filter = allowed.get(entry.getKey());
            if (filter == null) {
                throw ApiException.badRequest("筛选条件不合法：" + entry.getKey());
            }
            String value = text(entry.getValue());
            if (value.isBlank()) continue;
            if (value.length() > 120) {
                throw ApiException.badRequest(filter.label() + "内容过长");
            }
            if ("date".equals(filter.type())) {
                parseDate(value, filter.label());
            }
            if ("select".equals(filter.type())
                    && filter.options().stream().noneMatch(option -> option.value().equals(value))) {
                throw ApiException.badRequest(filter.label() + "选项不合法");
            }
            result.put(filter.id(), value);
        }
        validateDateRange(result);
        return result;
    }

    private List<Map<String, Object>> query(String source, Map<String, String> filters) {
        return switch (source) {
            case "members" -> queryMembers(filters);
            case "attendance" -> queryAttendance(filters);
            case "training" -> queryTraining(filters);
            case "schedule" -> querySchedule(filters);
            case "repairs" -> queryRepairs(filters);
            case "logs" -> queryLogs(filters);
            default -> throw ApiException.badRequest("导出数据源不存在");
        };
    }

    private List<Map<String, Object>> queryMembers(Map<String, String> filters) {
        QueryBuilder query = new QueryBuilder("""
                SELECT u.student_no AS studentNo,
                       u.name AS name,
                       CASE u.role
                         WHEN 'MEMBER' THEN '成员'
                         WHEN 'MINISTER' THEN '部长'
                         WHEN 'PRESIDENT' THEN '会长'
                         WHEN 'ADMIN' THEN '管理员'
                         ELSE u.role
                       END AS role,
                       CASE u.status WHEN 'ACTIVE' THEN '启用' WHEN 'DISABLED' THEN '停用' ELSE u.status END AS status,
                       u.phone AS phone,
                       u.major AS major,
                       u.grade AS grade,
                       u.qq AS qq,
                       u.last_login_at AS lastLoginAt,
                       u.created_at AS createdAt
                FROM users u
                WHERE 1 = 1
                """);
        query.keyword(filters.get("keyword"), "u.student_no", "u.name", "u.phone", "u.major", "u.grade");
        query.equals(filters.get("role"), "u.role");
        query.equals(filters.get("status"), "u.status");
        query.equals(filters.get("grade"), "u.grade");
        query.append(" ORDER BY CASE u.role WHEN 'ADMIN' THEN 1 WHEN 'PRESIDENT' THEN 2 WHEN 'MINISTER' THEN 3 ELSE 4 END, u.student_no");
        return limited(query);
    }

    private List<Map<String, Object>> queryAttendance(Map<String, String> filters) {
        QueryBuilder query = new QueryBuilder("""
                SELECT a.duty_date AS dutyDate,
                       a.student_no_snapshot AS studentNo,
                       a.name_snapshot AS name,
                       a.check_in_time AS checkInTime,
                       a.check_out_time AS checkOutTime,
                       CASE a.check_in_status
                         WHEN 'PENDING' THEN '待审核' WHEN 'APPROVED' THEN '已通过'
                         WHEN 'AUTO_APPROVED' THEN '自动通过' WHEN 'REJECTED' THEN '已驳回'
                         ELSE a.check_in_status END AS checkInStatus,
                       CASE a.check_out_status
                         WHEN 'NOT_SUBMITTED' THEN '未签退' WHEN 'PENDING' THEN '待审核'
                         WHEN 'APPROVED' THEN '已通过' WHEN 'AUTO_APPROVED' THEN '自动通过'
                         WHEN 'REJECTED' THEN '已驳回' ELSE a.check_out_status END AS checkOutStatus,
                       CASE a.effective_status
                         WHEN 'VALID' THEN '有效' WHEN 'PENDING' THEN '待审核'
                         WHEN 'INCOMPLETE' THEN '未签退' WHEN 'INVALID' THEN '无效'
                         ELSE a.effective_status END AS effectiveStatus,
                       a.duration_minutes AS durationMinutes,
                       a.valid_hours AS validHours,
                       CASE a.is_duty_day WHEN 1 THEN '是' ELSE '否' END AS dutyDay,
                       CASE a.within_duty_period WHEN 1 THEN '是' ELSE '否' END AS withinDutyPeriod,
                       CASE a.source WHEN 'PUBLIC' THEN '签到台' WHEN 'ADMIN_MANUAL' THEN '后台补录' ELSE a.source END AS source,
                       a.manual_reason AS reason
                FROM attendance_records a
                WHERE 1 = 1
                """);
        query.dateRange(filters, "a.duty_date");
        query.keyword(filters.get("keyword"), "a.student_no_snapshot", "a.name_snapshot");
        query.equals(filters.get("effectiveStatus"), "a.effective_status");
        query.append(" ORDER BY a.duty_date DESC, a.check_in_time DESC, a.id DESC");
        return limited(query);
    }

    private List<Map<String, Object>> queryTraining(Map<String, String> filters) {
        QueryBuilder query = new QueryBuilder("""
                SELECT s.training_date AS trainingDate,
                       s.title AS title,
                       s.start_time AS startTime,
                       s.end_time AS endTime,
                       s.location AS location,
                       s.speaker AS speaker,
                       p.student_no_snapshot AS studentNo,
                       p.name_snapshot AS name,
                       p.duration_hours AS durationHours,
                       p.remark AS remark
                FROM training_sessions s
                LEFT JOIN training_participants p ON p.session_id = s.id
                WHERE s.status <> 'ARCHIVED'
                """);
        query.dateRange(filters, "s.training_date");
        query.keyword(filters.get("keyword"), "s.title", "s.location", "s.speaker", "p.student_no_snapshot", "p.name_snapshot");
        query.append(" ORDER BY s.training_date DESC, s.id DESC, p.id");
        return limited(query);
    }

    private List<Map<String, Object>> querySchedule(Map<String, String> filters) {
        QueryBuilder query = new QueryBuilder("""
                SELECT CASE s.weekday
                         WHEN 1 THEN '星期一' WHEN 2 THEN '星期二' WHEN 3 THEN '星期三'
                         WHEN 4 THEN '星期四' WHEN 5 THEN '星期五' WHEN 6 THEN '星期六'
                         WHEN 7 THEN '星期日' ELSE CAST(s.weekday AS TEXT)
                       END AS weekday,
                       substr(s.start_time, 1, 5) || '-' || substr(s.end_time, 1, 5) AS period,
                       s.title AS title,
                       s.location AS location,
                       s.note AS note,
                       CASE s.enabled WHEN 1 THEN '显示' ELSE '隐藏' END AS enabled,
                       a.student_no_snapshot AS studentNo,
                       a.name_snapshot AS name,
                       a.sort_order + 1 AS sortOrder
                FROM duty_schedule_slots s
                LEFT JOIN duty_schedule_assignees a ON a.slot_id = s.id
                WHERE s.status = 'ACTIVE'
                """);
        query.equals(filters.get("weekday"), "CAST(s.weekday AS TEXT)");
        query.equals(filters.get("enabled"), "CAST(s.enabled AS TEXT)");
        query.append(" ORDER BY s.weekday, s.start_time, s.end_time, s.id, a.sort_order, a.id");
        return limited(query);
    }

    private List<Map<String, Object>> queryRepairs(Map<String, String> filters) {
        QueryBuilder query = new QueryBuilder("""
                SELECT r.case_no AS caseNo,
                       CASE r.status
                         WHEN 'COMPLETED' THEN '已完成' WHEN 'CANCELED' THEN '已取消'
                         ELSE '进行中' END AS status,
                       CASE r.agreement_type WHEN 'PUBLIC_DEVICE' THEN '免责协议' ELSE '维修协议' END AS agreementType,
                       r.received_at AS receivedAt,
                       r.completed_at AS completedAt,
                       r.owner_name AS ownerName,
                       r.owner_phone AS ownerPhone,
                       r.device_type AS deviceType,
                       r.device_brand AS deviceBrand,
                       r.device_model AS deviceModel,
                       r.accessories AS accessories,
                       r.fault_description AS faultDescription,
                       r.service_description AS serviceDescription,
                       CASE r.data_backup_confirmed WHEN 1 THEN '是' ELSE '否' END AS backupConfirmed,
                       CASE r.risk_acknowledged WHEN 1 THEN '是' ELSE '否' END AS riskAcknowledged,
                       CASE r.privacy_acknowledged WHEN 1 THEN '是' ELSE '否' END AS privacyAcknowledged,
                       r.handler_name_snapshot AS handlerName,
                       r.remark AS remark
                FROM repair_cases r
                WHERE r.deleted_at IS NULL
                """);
        query.dateTimeRange(filters, "r.received_at");
        query.keyword(filters.get("keyword"),
                "r.case_no", "r.owner_name", "r.owner_phone", "r.device_type", "r.device_brand",
                "r.device_model", "r.fault_description", "r.service_description", "r.handler_name_snapshot");
        String status = filters.get("status");
        if ("REPAIRING".equals(status)) {
            query.append(" AND r.status IN ('RECEIVED', 'DIAGNOSING', 'REPAIRING', 'WAITING_PICKUP')");
        } else {
            query.equals(status, "r.status");
        }
        query.append(" ORDER BY r.received_at DESC, r.id DESC");
        return limited(query);
    }

    private List<Map<String, Object>> queryLogs(Map<String, String> filters) {
        QueryBuilder query = new QueryBuilder("""
                SELECT l.created_at AS createdAt,
                       l.operator_student_no AS operatorStudentNo,
                       l.operator_name AS operatorName,
                       l.action_type AS actionType,
                       l.target_type AS targetType,
                       l.target_id AS targetId,
                       l.reason AS reason,
                       l.before_data AS beforeData,
                       l.after_data AS afterData
                FROM operation_logs l
                WHERE 1 = 1
                """);
        query.dateTimeRange(filters, "l.created_at");
        query.keyword(filters.get("keyword"),
                "l.operator_student_no", "l.operator_name", "l.action_type", "l.target_type", "l.reason");
        query.equals(filters.get("actionType"), "l.action_type");
        query.append(" ORDER BY l.created_at DESC, l.id DESC");
        return limited(query);
    }

    private List<Map<String, Object>> limited(QueryBuilder query) {
        query.append(" LIMIT " + (MAX_ROWS + 1));
        return jdbc.queryForList(query.sql(), query.args().toArray());
    }

    private void writeWorkbook(Workbook workbook,
                               SourceDefinition source,
                               List<FieldOption> fields,
                               Map<String, String> filters,
                               List<Map<String, Object>> rows) {
        Sheet sheet = workbook.createSheet(source.label());
        CellStyle titleStyle = titleStyle(workbook);
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle textStyle = textStyle(workbook);

        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue(source.label() + "自定义导出");
        title.getCell(0).setCellStyle(titleStyle);
        if (fields.size() > 1) sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, fields.size() - 1));

        Row meta = sheet.createRow(1);
        meta.createCell(0).setCellValue("导出时间：" + LocalDateTime.now().format(DISPLAY_TIME) + "    数据行数：" + rows.size());
        if (fields.size() > 1) sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, fields.size() - 1));

        Row header = sheet.createRow(3);
        for (int column = 0; column < fields.size(); column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(fields.get(column).label());
            cell.setCellStyle(headerStyle);
        }

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Map<String, Object> values = rows.get(rowIndex);
            Row row = sheet.createRow(rowIndex + 4);
            for (int column = 0; column < fields.size(); column++) {
                Cell cell = row.createCell(column);
                setCellValue(cell, values.get(fields.get(column).id()));
                cell.setCellStyle(textStyle);
            }
        }
        sheet.createFreezePane(0, 4);
        for (int column = 0; column < fields.size(); column++) {
            sheet.autoSizeColumn(column);
            sheet.setColumnWidth(column, Math.min(Math.max(sheet.getColumnWidth(column) + 768, 12 * 256), 42 * 256));
        }
    }

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool ? "是" : "否");
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private byte[] workbookBytes(WorkbookWriter writer) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writer.write(workbook);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw ApiException.badRequest("生成自定义 Excel 失败");
        }
    }

    private CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Microsoft YaHei");
        font.setFontHeightInPoints((short) 15);
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = borderedStyle(workbook);
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setFontName("Microsoft YaHei");
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle textStyle(Workbook workbook) {
        CellStyle style = borderedStyle(workbook);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        Font font = workbook.createFont();
        font.setFontName("Microsoft YaHei");
        style.setFont(font);
        return style;
    }

    private CellStyle borderedStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    private String filename(String requested, String sourceLabel) {
        String value = text(requested);
        if (value.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            value = value.substring(0, value.length() - 5);
        }
        value = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (value.isBlank()) value = sourceLabel + "_" + LocalDateTime.now().format(FILE_TIME);
        if (value.length() > 80) value = value.substring(0, 80);
        return value + ".xlsx";
    }

    private void validateDateRange(Map<String, String> filters) {
        String from = filters.get("from");
        String to = filters.get("to");
        if (from != null && to != null && parseDate(from, "开始日期").isAfter(parseDate(to, "结束日期"))) {
            throw ApiException.badRequest("开始日期不能晚于结束日期");
        }
    }

    private LocalDate parseDate(String value, String label) {
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            throw ApiException.badRequest(label + "格式不正确");
        }
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private void requireExporter(AuthUser current) {
        if (current.role() != Role.PRESIDENT && current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有会长或管理员可以自定义导出 Excel");
        }
    }

    private static SourceDefinition membersSource() {
        return new SourceDefinition("members", "成员", false, List.of(
                field("studentNo", "学号", true), field("name", "姓名", true),
                field("role", "角色", true), field("status", "账号状态", true),
                field("phone", "手机号", true), field("major", "学院", true),
                field("grade", "年级", true), field("qq", "QQ", false),
                field("lastLoginAt", "最近登录", false), field("createdAt", "创建时间", false)
        ), List.of(
                textFilter("keyword", "关键词"),
                selectFilter("role", "角色", choices(
                        "MEMBER", "成员", "MINISTER", "部长", "PRESIDENT", "会长", "ADMIN", "管理员")),
                selectFilter("status", "账号状态", choices("ACTIVE", "启用", "DISABLED", "停用")),
                textFilter("grade", "年级")
        ));
    }

    private static SourceDefinition attendanceSource() {
        return new SourceDefinition("attendance", "值班记录", false, List.of(
                field("dutyDate", "值班日期", true), field("studentNo", "学号", true),
                field("name", "姓名", true), field("checkInTime", "签到时间", true),
                field("checkOutTime", "签退时间", true), field("checkInStatus", "签到审核", false),
                field("checkOutStatus", "签退审核", false), field("effectiveStatus", "有效状态", true),
                field("durationMinutes", "实际分钟", false), field("validHours", "有效时长", true),
                field("dutyDay", "是否值班日", false), field("withinDutyPeriod", "是否值班时段", false),
                field("source", "记录来源", false),
                field("reason", "补录原因", false)
        ), List.of(
                dateFilter("from", "开始日期", startOfYear()), dateFilter("to", "结束日期", LocalDate.now().toString()),
                textFilter("keyword", "学号或姓名"),
                selectFilter("effectiveStatus", "有效状态", choices(
                        "VALID", "有效", "PENDING", "待审核", "INCOMPLETE", "未签退", "INVALID", "无效"))
        ));
    }

    private static SourceDefinition trainingSource() {
        return new SourceDefinition("training", "培训记录", false, List.of(
                field("trainingDate", "培训日期", true), field("title", "培训名称", true),
                field("startTime", "开始时间", false), field("endTime", "结束时间", false),
                field("location", "地点", true), field("speaker", "主讲人", true),
                field("studentNo", "学号", true), field("name", "参与人", true),
                field("durationHours", "计入时长", true), field("remark", "备注", false)
        ), List.of(
                dateFilter("from", "开始日期", startOfYear()), dateFilter("to", "结束日期", LocalDate.now().toString()),
                textFilter("keyword", "培训或成员关键词")
        ));
    }

    private static SourceDefinition scheduleSource() {
        return new SourceDefinition("schedule", "部长排班", false, List.of(
                field("weekday", "星期", true), field("period", "值班时段", true),
                field("title", "标题", true), field("location", "地点", false),
                field("note", "备注", false), field("enabled", "签到台显示", false),
                field("studentNo", "学号", true), field("name", "姓名", true),
                field("sortOrder", "组内顺序", false)
        ), List.of(
                selectFilter("weekday", "星期", choices(
                        "1", "星期一", "2", "星期二", "3", "星期三", "4", "星期四",
                        "5", "星期五", "6", "星期六", "7", "星期日")),
                selectFilter("enabled", "签到台显示", choices("1", "显示", "0", "隐藏"))
        ));
    }

    private static SourceDefinition repairSource() {
        return new SourceDefinition("repairs", "维修事务", false, List.of(
                field("caseNo", "维修编号", true), field("status", "状态", true),
                field("agreementType", "协议类型", true), field("receivedAt", "接收时间", true),
                field("completedAt", "完成时间", false), field("ownerName", "送修人", true),
                field("ownerPhone", "联系方式", true), field("deviceType", "设备类型", true),
                field("deviceBrand", "品牌", false), field("deviceModel", "型号", true),
                field("accessories", "随附物品", false), field("faultDescription", "故障描述", true),
                field("serviceDescription", "处理记录", true), field("backupConfirmed", "数据备份提醒", false),
                field("riskAcknowledged", "风险确认", false), field("privacyAcknowledged", "隐私确认", false),
                field("handlerName", "处理人", true), field("remark", "备注", false)
        ), List.of(
                dateFilter("from", "开始日期", startOfYear()), dateFilter("to", "结束日期", LocalDate.now().toString()),
                textFilter("keyword", "维修关键词"),
                selectFilter("status", "状态", choices(
                        "REPAIRING", "进行中", "COMPLETED", "已完成", "CANCELED", "已取消"))
        ));
    }

    private static SourceDefinition logsSource() {
        return new SourceDefinition("logs", "操作日志", true, List.of(
                field("createdAt", "操作时间", true), field("operatorStudentNo", "操作人账号", false),
                field("operatorName", "操作人", true), field("actionType", "操作类型", true),
                field("targetType", "对象类型", true), field("targetId", "对象 ID", false),
                field("reason", "原因", true), field("beforeData", "修改前", false),
                field("afterData", "修改后", false)
        ), List.of(
                dateFilter("from", "开始日期", startOfYear()), dateFilter("to", "结束日期", LocalDate.now().toString()),
                textFilter("keyword", "操作人或内容关键词"), textFilter("actionType", "操作类型")
        ));
    }

    private static FieldOption field(String id, String label, boolean defaultSelected) {
        return new FieldOption(id, label, defaultSelected);
    }

    private static FilterOption textFilter(String id, String label) {
        return new FilterOption(id, label, "text", "", List.of());
    }

    private static FilterOption dateFilter(String id, String label, String defaultValue) {
        return new FilterOption(id, label, "date", defaultValue, List.of());
    }

    private static FilterOption selectFilter(String id, String label, List<ChoiceOption> options) {
        return new FilterOption(id, label, "select", "", options);
    }

    private static List<ChoiceOption> choices(String... values) {
        List<ChoiceOption> result = new ArrayList<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.add(new ChoiceOption(values[index], values[index + 1]));
        }
        return List.copyOf(result);
    }

    private static String startOfYear() {
        return LocalDate.of(LocalDate.now().getYear(), 1, 1).toString();
    }

    public record ExportOptions(List<SourceOption> sources) {
    }

    public record SourceOption(String id, String label, List<FieldOption> fields, List<FilterOption> filters) {
    }

    public record FieldOption(String id, String label, boolean defaultSelected) {
    }

    public record FilterOption(String id, String label, String type, String defaultValue, List<ChoiceOption> options) {
    }

    public record ChoiceOption(String value, String label) {
    }

    public record ExportRequest(String source, List<String> fields, Map<String, String> filters, String filename) {
    }

    public record ExportFile(String filename, byte[] bytes, int rowCount) {
    }

    public record ExportPreview(
            String source,
            String sourceLabel,
            List<FieldOption> fields,
            Map<String, String> filters,
            int totalRows,
            boolean truncated,
            List<Map<String, Object>> rows
    ) {
    }

    private record PreparedExport(
            SourceDefinition source,
            List<FieldOption> fields,
            Map<String, String> filters
    ) {
    }

    private record SourceDefinition(
            String id,
            String label,
            boolean adminOnly,
            List<FieldOption> fields,
            List<FilterOption> filters
    ) {
    }

    private static final class QueryBuilder {
        private final StringBuilder sql;
        private final List<Object> args = new ArrayList<>();

        private QueryBuilder(String baseSql) {
            this.sql = new StringBuilder(baseSql);
        }

        private void append(String value) {
            sql.append(value);
        }

        private void equals(String value, String column) {
            if (value == null || value.isBlank()) return;
            sql.append(" AND ").append(column).append(" = ?");
            args.add(value);
        }

        private void keyword(String value, String... columns) {
            if (value == null || value.isBlank()) return;
            sql.append(" AND (");
            for (int index = 0; index < columns.length; index++) {
                if (index > 0) sql.append(" OR ");
                sql.append(columns[index]).append(" LIKE ?");
                args.add("%" + value + "%");
            }
            sql.append(')');
        }

        private void dateRange(Map<String, String> filters, String column) {
            String from = filters.get("from");
            String to = filters.get("to");
            if (from != null && !from.isBlank()) {
                sql.append(" AND ").append(column).append(" >= ?");
                args.add(from);
            }
            if (to != null && !to.isBlank()) {
                sql.append(" AND ").append(column).append(" <= ?");
                args.add(to);
            }
        }

        private void dateTimeRange(Map<String, String> filters, String column) {
            String from = filters.get("from");
            String to = filters.get("to");
            if (from != null && !from.isBlank()) {
                sql.append(" AND ").append(column).append(" >= ?");
                args.add(from + " 00:00:00");
            }
            if (to != null && !to.isBlank()) {
                sql.append(" AND ").append(column).append(" < ?");
                args.add(LocalDate.parse(to).plusDays(1) + " 00:00:00");
            }
        }

        private String sql() {
            return sql.toString();
        }

        private List<Object> args() {
            return args;
        }
    }

    private interface WorkbookWriter {
        void write(Workbook workbook);
    }
}
