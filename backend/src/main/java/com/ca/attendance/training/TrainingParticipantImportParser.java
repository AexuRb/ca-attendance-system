package com.ca.attendance.training;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.ExcelCellTextReader;
import com.ca.attendance.common.ExcelImportPolicy;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class TrainingParticipantImportParser {
    private static final Pattern STUDENT_NO_PATTERN = Pattern.compile("\\d{1,32}");
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "^(-?\\d+(?:\\.\\d+)?)\\s*(?:h|H|小时|时)?$"
    );
    private static final BigDecimal MAX_DURATION_HOURS = new BigDecimal("999.99");
    private static final int ISSUE_LIMIT = 20;

    public ParseResult parse(Sheet sheet, BigDecimal defaultDuration) {
        ExcelCellTextReader reader = new ExcelCellTextReader(sheet.getWorkbook());
        int headerIndex = findHeaderRow(sheet, reader);
        Map<String, Integer> columns = headerIndex >= 0
                ? headerColumns(sheet.getRow(headerIndex), reader)
                : fallbackColumns();
        int startRow = headerIndex >= 0 ? headerIndex + 1 : 0;
        ExcelImportPolicy.validateRowCount(sheet, startRow, "培训名单");

        List<ParsedRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int skipped = 0;

        for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            String studentNo = cell(row, columns.get("studentNo"), reader).replaceAll("\\s+", "");
            String name = cell(row, columns.get("name"), reader).trim();
            String duration = cell(row, columns.get("duration"), reader);
            String remark = cell(row, columns.get("remark"), reader);
            if (studentNo.isBlank() && name.isBlank() && duration.isBlank() && remark.isBlank()) {
                continue;
            }
            if (name.isBlank()) {
                skipped++;
                addIssue(errors, "第 " + (i + 1) + " 行：缺少姓名");
                continue;
            }
            String seenKey = studentNo.isBlank() ? "name:" + name : "student:" + studentNo;
            if (!seen.add(seenKey)) {
                skipped++;
                addIssue(errors, "第 " + (i + 1) + " 行：名单在本次文件中重复");
                continue;
            }
            try {
                if (!studentNo.isBlank() && !STUDENT_NO_PATTERN.matcher(studentNo).matches()) {
                    throw ApiException.badRequest("学号格式不正确");
                }
                rows.add(new ParsedRow(
                        i + 1,
                        studentNo,
                        name,
                        parseDuration(duration, defaultDuration),
                        trimToNull(remark, 500)
                ));
            } catch (ApiException ex) {
                skipped++;
                addIssue(errors, "第 " + (i + 1) + " 行：" + ex.getMessage());
            }
        }
        return new ParseResult(rows, skipped, errors);
    }

    private int findHeaderRow(Sheet sheet, ExcelCellTextReader reader) {
        int last = Math.min(sheet.getLastRowNum(), 8);
        for (int rowIndex = 0; rowIndex <= last; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            boolean hasStudentNo = false;
            boolean hasName = false;
            for (int col = Math.max(row.getFirstCellNum(), 0); col < row.getLastCellNum(); col++) {
                String header = reader.read(row, col);
                if (header.contains("学号") || header.equalsIgnoreCase("studentNo")) {
                    hasStudentNo = true;
                }
                if (header.contains("姓名") || header.equalsIgnoreCase("name")) {
                    hasName = true;
                }
            }
            if (hasStudentNo && hasName) {
                return rowIndex;
            }
        }
        return -1;
    }

    private Map<String, Integer> headerColumns(Row row, ExcelCellTextReader reader) {
        Map<String, Integer> columns = new HashMap<>();
        for (int col = Math.max(row.getFirstCellNum(), 0); col < row.getLastCellNum(); col++) {
            String header = reader.read(row, col).toLowerCase(Locale.ROOT);
            if (header.contains("学号") || header.contains("student")) {
                columns.putIfAbsent("studentNo", col);
            }
            if (header.contains("姓名") || header.equals("name")) {
                columns.putIfAbsent("name", col);
            }
            if (header.contains("时长") || header.contains("小时") || header.contains("duration") || header.contains("hours")) {
                columns.putIfAbsent("duration", col);
            }
            if (header.contains("备注") || header.contains("说明") || header.contains("remark")) {
                columns.putIfAbsent("remark", col);
            }
        }
        if (!columns.containsKey("studentNo") || !columns.containsKey("name")) {
            return fallbackColumns();
        }
        columns.putIfAbsent("duration", -1);
        columns.putIfAbsent("remark", -1);
        return columns;
    }

    private Map<String, Integer> fallbackColumns() {
        return Map.of("studentNo", 0, "name", 1, "duration", 2, "remark", 3);
    }

    private String cell(Row row, Integer index, ExcelCellTextReader reader) {
        if (index == null || index < 0) {
            return "";
        }
        return reader.read(row, index);
    }

    private BigDecimal parseDuration(String value, BigDecimal defaultDuration) {
        if (value == null || value.isBlank()) {
            return normalizeDuration(defaultDuration);
        }
        var matcher = DURATION_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            throw ApiException.badRequest("培训时长应填写数字，可选单位 h、小时或时");
        }
        try {
            return normalizeDuration(new BigDecimal(matcher.group(1)));
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("培训时长应填写数字，可选单位 h、小时或时");
        }
    }

    private BigDecimal normalizeDuration(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("培训时长不能为负数");
        }
        if (normalized.compareTo(MAX_DURATION_HOURS) > 0) {
            throw ApiException.badRequest("培训时长不能超过 999.99 小时");
        }
        return normalized.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private void addIssue(List<String> issues, String issue) {
        if (issues.size() < ISSUE_LIMIT) {
            issues.add(issue);
        }
    }

    public record ParsedRow(
            int rowNumber,
            String studentNo,
            String name,
            BigDecimal durationHours,
            String remark
    ) {
    }

    public record ParseResult(List<ParsedRow> rows, int skipped, List<String> errors) {
        public ParseResult {
            rows = List.copyOf(rows);
            errors = List.copyOf(errors);
        }
    }
}
