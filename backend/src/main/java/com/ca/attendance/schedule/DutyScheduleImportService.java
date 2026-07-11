package com.ca.attendance.schedule;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.settings.DutyPeriodService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ca.attendance.common.JdbcTime.databaseTime;

@Service
public class DutyScheduleImportService {
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 1000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern STUDENT_NO_PATTERN = Pattern.compile("\\d{1,32}");
    private static final Pattern PERIOD_PATTERN = Pattern.compile(
            "^(\\d{1,2}[:：]\\d{2})\\s*(?:-|–|—|~|～|至)\\s*(\\d{1,2}[:：]\\d{2})$"
    );

    private final JdbcTemplate jdbc;
    private final OperationLogService logs;
    private final DutyPeriodService dutyPeriods;

    public DutyScheduleImportService(JdbcTemplate jdbc,
                                     OperationLogService logs,
                                     DutyPeriodService dutyPeriods) {
        this.jdbc = jdbc;
        this.logs = logs;
        this.dutyPeriods = dutyPeriods;
    }

    public ExportFile exportTemplate() {
        requireManage(AuthContext.current());
        List<ConfiguredPeriod> periods = configuredPeriods();
        List<WeekdayValue> weekdays = enabledWeekdays();
        if (periods.isEmpty()) {
            throw ApiException.badRequest("请先在值班设置中保存值班时间段");
        }
        if (weekdays.isEmpty()) {
            throw ApiException.badRequest("请先在值班设置中启用至少一个值班星期");
        }
        return new ExportFile("部长排班导入模板.xlsx", workbookBytes(workbook -> writeTemplate(workbook, weekdays, periods)));
    }

    public ImportPreview preview(MultipartFile file) {
        requireManage(AuthContext.current());
        return toPreview(parse(file));
    }

    @Transactional
    public ImportResult importSchedules(MultipartFile file) {
        AuthUser current = AuthContext.current();
        requireManage(current);
        ParsedImport parsed = parse(file);
        if (!parsed.issues().isEmpty()) {
            ImportIssue issue = parsed.issues().getFirst();
            String rowText = issue.row() > 0 ? "第 " + issue.row() + " 行" : "文件";
            throw ApiException.badRequest("排班文件校验未通过：" + rowText + " " + issue.message());
        }

        int createdGroups = 0;
        int updatedGroups = 0;
        int archivedDuplicateSlots = 0;
        int assignedMembers = 0;
        List<ImportedGroup> importedGroups = new ArrayList<>();
        for (ParsedGroup group : parsed.groups()) {
            ReplaceOutcome outcome = replaceGroup(group, current.id());
            if (outcome.created()) {
                createdGroups++;
            } else {
                updatedGroups++;
            }
            archivedDuplicateSlots += outcome.archivedDuplicateSlots();
            assignedMembers += group.members().size();
            importedGroups.add(new ImportedGroup(
                    outcome.slotId(),
                    group.key().weekday(),
                    weekdayName(group.key().weekday()),
                    timeText(group.key().startTime()),
                    timeText(group.key().endTime()),
                    group.members().size()
            ));
        }

        ImportResult result = new ImportResult(
                parsed.groups().size(),
                assignedMembers,
                createdGroups,
                updatedGroups,
                archivedDuplicateSlots,
                importedGroups
        );
        logs.log(
                "IMPORT_DUTY_SCHEDULES",
                "duty_schedule_slots",
                null,
                Map.of("groups", parsed.groups().stream().map(group -> group.key().displayKey()).toList()),
                result,
                "Excel 批量导入部长排班"
        );
        return result;
    }

    private ParsedImport parse(MultipartFile file) {
        validateFile(file);
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                return invalidImport(new ImportIssue(0, "file", "Excel 文件没有工作表"));
            }
            return parseSheet(workbook.getSheetAt(0));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("Excel 文件读取失败，请确认文件格式正确");
        }
    }

    private ParsedImport parseSheet(Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        HeaderColumns header = findHeader(sheet, formatter);
        if (header == null) {
            return invalidImport(new ImportIssue(0, "header", "未找到“星期、值班时段、学号、姓名”表头"));
        }
        List<ImportIssue> issues = new ArrayList<>();
        if (header.weekday() < 0) issues.add(new ImportIssue(header.row() + 1, "weekday", "缺少“星期”列"));
        if (header.period() < 0) issues.add(new ImportIssue(header.row() + 1, "period", "缺少“值班时段”列"));
        if (header.studentNo() < 0) issues.add(new ImportIssue(header.row() + 1, "studentNo", "缺少“学号”列"));
        if (header.name() < 0) issues.add(new ImportIssue(header.row() + 1, "name", "缺少“姓名”列"));
        if (!issues.isEmpty()) {
            return new ParsedImport(0, List.of(), issues);
        }

        Map<String, ConfiguredPeriod> periods = new LinkedHashMap<>();
        for (ConfiguredPeriod period : configuredPeriods()) {
            periods.put(period.key(), period);
        }
        Set<Integer> weekdays = enabledWeekdays().stream()
                .map(WeekdayValue::weekday)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, UserRef> users = usersByStudentNo();
        Map<GroupKey, List<ValidatedMember>> grouped = new LinkedHashMap<>();
        Map<GroupKey, Set<String>> seenByGroup = new HashMap<>();
        int sourceRows = 0;
        int lastRow = Math.min(sheet.getLastRowNum(), header.row() + MAX_ROWS);

        for (int rowIndex = header.row() + 1; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            String weekdayText = cell(row, header.weekday(), formatter);
            String periodText = cell(row, header.period(), formatter);
            String studentNo = cell(row, header.studentNo(), formatter).replaceAll("\\s+", "");
            String suppliedName = cell(row, header.name(), formatter);
            if (weekdayText.isBlank() && periodText.isBlank() && studentNo.isBlank() && suppliedName.isBlank()) {
                continue;
            }
            if (studentNo.isBlank() && suppliedName.isBlank() && !weekdayText.isBlank() && !periodText.isBlank()) {
                continue;
            }
            sourceRows++;
            int excelRow = rowIndex + 1;
            int issueCountBefore = issues.size();

            Integer weekday = parseWeekday(weekdayText);
            if (weekday == null) {
                issues.add(new ImportIssue(excelRow, "weekday", "星期格式不正确"));
            } else if (!weekdays.contains(weekday)) {
                issues.add(new ImportIssue(excelRow, "weekday", weekdayName(weekday) + "当前未启用"));
            }

            ConfiguredPeriod period = parseConfiguredPeriod(periodText, periods);
            if (period == null) {
                issues.add(new ImportIssue(excelRow, "period", "值班时段必须使用设置中已有的时段"));
            }

            if (studentNo.isBlank()) {
                issues.add(new ImportIssue(excelRow, "studentNo", "学号不能为空"));
            } else if (!STUDENT_NO_PATTERN.matcher(studentNo).matches()) {
                issues.add(new ImportIssue(excelRow, "studentNo", "学号格式不正确"));
            }

            UserRef user = studentNo.isBlank() ? null : users.get(studentNo);
            if (!studentNo.isBlank() && user == null) {
                issues.add(new ImportIssue(excelRow, "studentNo", "未找到该成员账号"));
            } else if (user != null && !"ACTIVE".equals(user.status())) {
                issues.add(new ImportIssue(excelRow, "studentNo", "该成员账号已停用"));
            } else if (user != null && !Set.of("MINISTER", "PRESIDENT", "ADMIN").contains(user.role())) {
                issues.add(new ImportIssue(excelRow, "studentNo", "排班成员必须是部长、会长或管理员"));
            }
            if (user != null && !suppliedName.isBlank() && !user.name().equals(suppliedName.trim())) {
                issues.add(new ImportIssue(excelRow, "name", "姓名与成员账号不一致，应为“" + user.name() + "”"));
            }

            if (issues.size() > issueCountBefore) {
                continue;
            }
            GroupKey key = new GroupKey(weekday, period.startTime(), period.endTime());
            Set<String> seen = seenByGroup.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
            if (!seen.add(studentNo)) {
                issues.add(new ImportIssue(excelRow, "studentNo", "同一星期和时段内学号重复"));
                continue;
            }
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new ValidatedMember(user.id(), user.studentNo(), user.name()));
        }
        if (sheet.getLastRowNum() > lastRow) {
            issues.add(new ImportIssue(lastRow + 2, "file", "文件最多导入 " + MAX_ROWS + " 行"));
        }

        List<ParsedGroup> groups = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator
                        .comparingInt(GroupKey::weekday)
                        .thenComparing(GroupKey::startTime)
                        .thenComparing(GroupKey::endTime)))
                .map(entry -> new ParsedGroup(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        if (groups.isEmpty() && issues.isEmpty()) {
            issues.add(new ImportIssue(0, "file", "没有可导入的排班成员，请填写学号"));
        }
        return new ParsedImport(sourceRows, groups, List.copyOf(issues));
    }

    private ImportPreview toPreview(ParsedImport parsed) {
        List<PreviewGroup> groups = parsed.groups().stream().map(group -> new PreviewGroup(
                group.key().weekday(),
                weekdayName(group.key().weekday()),
                timeText(group.key().startTime()),
                timeText(group.key().endTime()),
                group.members().stream()
                        .map(member -> new PreviewMember(member.studentNo(), member.name()))
                        .toList()
        )).toList();
        int memberCount = groups.stream().mapToInt(group -> group.members().size()).sum();
        return new ImportPreview(
                parsed.issues().isEmpty(),
                parsed.sourceRows(),
                groups.size(),
                memberCount,
                groups,
                parsed.issues()
        );
    }

    private ReplaceOutcome replaceGroup(ParsedGroup group, long operatorId) {
        GroupKey key = group.key();
        List<Long> existingIds = jdbc.queryForList("""
                SELECT id
                FROM duty_schedule_slots
                WHERE status = 'ACTIVE' AND weekday = ? AND start_time = ? AND end_time = ?
                ORDER BY id
                """, Long.class, key.weekday(), databaseTime(key.startTime()), databaseTime(key.endTime()));
        boolean created = existingIds.isEmpty();
        long slotId;
        int archivedDuplicates = 0;
        if (created) {
            Long inserted = jdbc.queryForObject("""
                    INSERT INTO duty_schedule_slots (
                      weekday, start_time, end_time, title, location, note, enabled, status, created_by, updated_by
                    )
                    VALUES (?, ?, ?, '部长值班', NULL, NULL, 1, 'ACTIVE', ?, ?)
                    RETURNING id
                    """, Long.class,
                    key.weekday(), databaseTime(key.startTime()), databaseTime(key.endTime()), operatorId, operatorId);
            slotId = inserted == null ? 0 : inserted;
        } else {
            slotId = existingIds.getFirst();
            jdbc.update("""
                    UPDATE duty_schedule_slots
                    SET enabled = 1, updated_by = ?, updated_at = datetime('now', 'localtime')
                    WHERE id = ?
                    """, operatorId, slotId);
            for (int index = 1; index < existingIds.size(); index++) {
                jdbc.update("""
                        UPDATE duty_schedule_slots
                        SET status = 'ARCHIVED', updated_by = ?, updated_at = datetime('now', 'localtime')
                        WHERE id = ?
                        """, operatorId, existingIds.get(index));
                archivedDuplicates++;
            }
        }

        jdbc.update("DELETE FROM duty_schedule_assignees WHERE slot_id = ?", slotId);
        for (int index = 0; index < group.members().size(); index++) {
            ValidatedMember member = group.members().get(index);
            jdbc.update("""
                    INSERT INTO duty_schedule_assignees (
                      slot_id, user_id, student_no_snapshot, name_snapshot, sort_order
                    )
                    VALUES (?, ?, ?, ?, ?)
                    """, slotId, member.id(), member.studentNo(), member.name(), index);
        }
        return new ReplaceOutcome(slotId, created, archivedDuplicates);
    }

    private HeaderColumns findHeader(Sheet sheet, DataFormatter formatter) {
        int last = Math.min(sheet.getLastRowNum(), 10);
        for (int rowIndex = 0; rowIndex <= last; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            int weekday = -1;
            int period = -1;
            int studentNo = -1;
            int name = -1;
            for (int column = Math.max(row.getFirstCellNum(), 0); column < row.getLastCellNum(); column++) {
                String value = cell(row, column, formatter).toLowerCase(Locale.ROOT);
                if (value.equals("星期") || value.equals("周") || value.contains("weekday")) weekday = column;
                else if (value.contains("时段") || value.contains("时间段") || value.contains("period")) period = column;
                else if (value.contains("学号") || value.contains("student")) studentNo = column;
                else if (value.contains("姓名") || value.equals("name")) name = column;
            }
            if (weekday >= 0 || period >= 0 || studentNo >= 0 || name >= 0) {
                return new HeaderColumns(rowIndex, weekday, period, studentNo, name);
            }
        }
        return null;
    }

    private ConfiguredPeriod parseConfiguredPeriod(String value, Map<String, ConfiguredPeriod> configured) {
        if (value == null || value.isBlank()) return null;
        Matcher matcher = PERIOD_PATTERN.matcher(value.trim());
        if (!matcher.matches()) return null;
        try {
            LocalTime start = LocalTime.parse(normalizeClock(matcher.group(1)));
            LocalTime end = LocalTime.parse(normalizeClock(matcher.group(2)));
            return configured.get(periodKey(start, end));
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer parseWeekday(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim().replace("星期", "").replace("周", "");
        return switch (text) {
            case "1", "一" -> 1;
            case "2", "二" -> 2;
            case "3", "三" -> 3;
            case "4", "四" -> 4;
            case "5", "五" -> 5;
            case "6", "六" -> 6;
            case "7", "日", "天" -> 7;
            default -> null;
        };
    }

    private Map<String, UserRef> usersByStudentNo() {
        Map<String, UserRef> result = new HashMap<>();
        jdbc.query("SELECT id, student_no, name, role, status FROM users", rs -> {
            UserRef user = new UserRef(
                    rs.getLong("id"),
                    rs.getString("student_no"),
                    rs.getString("name"),
                    rs.getString("role"),
                    rs.getString("status")
            );
            result.put(user.studentNo(), user);
        });
        return result;
    }

    private List<ConfiguredPeriod> configuredPeriods() {
        return dutyPeriods.list().stream()
                .map(item -> new ConfiguredPeriod(
                        LocalTime.parse(item.startTime()),
                        LocalTime.parse(item.endTime())
                ))
                .sorted(Comparator.comparing(ConfiguredPeriod::startTime).thenComparing(ConfiguredPeriod::endTime))
                .toList();
    }

    private List<WeekdayValue> enabledWeekdays() {
        return jdbc.query("""
                SELECT weekday, weekday_name
                FROM duty_weekday_settings
                WHERE enabled = 1
                ORDER BY weekday
                """, (rs, rowNum) -> new WeekdayValue(rs.getInt("weekday"), rs.getString("weekday_name")));
    }

    private void writeTemplate(Workbook workbook,
                               List<WeekdayValue> weekdays,
                               List<ConfiguredPeriod> periods) {
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle textStyle = textStyle(workbook);
        Sheet sheet = workbook.createSheet("排班导入");
        String[] headers = {"星期", "值班时段", "学号", "姓名"};
        Row header = sheet.createRow(0);
        for (int column = 0; column < headers.length; column++) {
            Cell cell = header.createCell(column);
            cell.setCellValue(headers[column]);
            cell.setCellStyle(headerStyle);
        }
        int rowIndex = 1;
        for (WeekdayValue weekday : weekdays) {
            for (ConfiguredPeriod period : periods) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(weekday.name());
                row.createCell(1).setCellValue(period.displayText());
                row.createCell(2).setCellValue("");
                row.createCell(3).setCellValue("");
                for (int column = 0; column < headers.length; column++) {
                    row.getCell(column).setCellStyle(textStyle);
                }
            }
        }
        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 20 * 256);
        sheet.setColumnWidth(2, 20 * 256);
        sheet.setColumnWidth(3, 18 * 256);
        sheet.createFreezePane(0, 1);

        Sheet notes = workbook.createSheet("填写说明");
        List<String> instructions = List.of(
                "排班导入说明",
                "1. 每行填写一名值班人员；同一星期和时段需要多人时，请复制该行继续填写。",
                "2. 学号必填，姓名可不填；填写姓名时必须与成员页面完全一致。",
                "3. 只能安排当前处于启用状态的部长、会长或管理员账号。",
                "4. 星期和值班时段只能使用模板中列出的内容。",
                "5. 预览发现任意错误时，整份文件都不会写入。",
                "6. 导入只覆盖文件中实际填写了学号的星期和时段，其他现有排班保持不变。"
        );
        for (int index = 0; index < instructions.size(); index++) {
            Row row = notes.createRow(index);
            Cell cell = row.createCell(0);
            cell.setCellValue(instructions.get(index));
            cell.setCellStyle(index == 0 ? headerStyle : textStyle);
        }
        notes.setColumnWidth(0, 76 * 256);
        workbook.setActiveSheet(0);
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
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
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setFontName("Microsoft YaHei");
        style.setFont(font);
        return style;
    }

    private byte[] workbookBytes(WorkbookWriter writer) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writer.write(workbook);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw ApiException.badRequest("生成排班模板失败");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择排班 Excel 文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw ApiException.badRequest("排班 Excel 文件不能超过 5 MB");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx")) {
            throw ApiException.badRequest("请选择 .xlsx 格式的排班文件");
        }
    }

    private void requireManage(AuthUser current) {
        if (current.role() != Role.PRESIDENT && current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有会长或管理员可以批量导入排班");
        }
    }

    private String cell(Row row, int column, DataFormatter formatter) {
        if (row == null || column < 0) return "";
        return formatter.formatCellValue(row.getCell(column)).trim();
    }

    private String normalizeClock(String value) {
        String text = value.replace('：', ':').trim();
        String[] parts = text.split(":");
        return String.format("%02d:%02d", Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private String periodKey(LocalTime start, LocalTime end) {
        return timeText(start) + "-" + timeText(end);
    }

    private String timeText(LocalTime value) {
        return value.format(TIME_FORMAT);
    }

    private String weekdayName(int weekday) {
        return switch (weekday) {
            case 1 -> "星期一";
            case 2 -> "星期二";
            case 3 -> "星期三";
            case 4 -> "星期四";
            case 5 -> "星期五";
            case 6 -> "星期六";
            case 7 -> "星期日";
            default -> "未知";
        };
    }

    private ParsedImport invalidImport(ImportIssue issue) {
        return new ParsedImport(0, List.of(), List.of(issue));
    }

    public record ExportFile(String filename, byte[] bytes) {
    }

    public record ImportPreview(
            boolean valid,
            int sourceRows,
            int groupCount,
            int memberCount,
            List<PreviewGroup> groups,
            List<ImportIssue> issues
    ) {
    }

    public record PreviewGroup(
            int weekday,
            String weekdayName,
            String startTime,
            String endTime,
            List<PreviewMember> members
    ) {
    }

    public record PreviewMember(String studentNo, String name) {
    }

    public record ImportIssue(int row, String field, String message) {
    }

    public record ImportResult(
            int replacedGroups,
            int assignedMembers,
            int createdGroups,
            int updatedGroups,
            int archivedDuplicateSlots,
            List<ImportedGroup> groups
    ) {
    }

    public record ImportedGroup(
            long slotId,
            int weekday,
            String weekdayName,
            String startTime,
            String endTime,
            int memberCount
    ) {
    }

    private record ParsedImport(int sourceRows, List<ParsedGroup> groups, List<ImportIssue> issues) {
    }

    private record ParsedGroup(GroupKey key, List<ValidatedMember> members) {
    }

    private record GroupKey(int weekday, LocalTime startTime, LocalTime endTime) {
        String displayKey() {
            return weekday + "@" + startTime + "-" + endTime;
        }
    }

    private record ValidatedMember(long id, String studentNo, String name) {
    }

    private record UserRef(long id, String studentNo, String name, String role, String status) {
    }

    private record ConfiguredPeriod(LocalTime startTime, LocalTime endTime) {
        String key() {
            return startTime.format(TIME_FORMAT) + "-" + endTime.format(TIME_FORMAT);
        }

        String displayText() {
            return key();
        }
    }

    private record WeekdayValue(int weekday, String name) {
    }

    private record HeaderColumns(int row, int weekday, int period, int studentNo, int name) {
    }

    private record ReplaceOutcome(long slotId, boolean created, int archivedDuplicateSlots) {
    }

    private interface WorkbookWriter {
        void write(Workbook workbook);
    }
}
