package com.ca.attendance.stats;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.common.ApiException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class StatsService {
    private static final DateTimeFormatter EXPORT_DATE = DateTimeFormatter.ofPattern("M月d日");
    private static final String[] WEEK_LABELS = {
            "第一周", "第二周", "第三周", "第四周", "第五周", "第六周", "第七周", "第八周", "第九周", "第十周",
            "第十一周", "第十二周", "第十三周", "第十四周", "第十五周", "第十六周", "第十七周", "第十八周",
            "第十九周", "第二十周", "第二十一周", "第二十二周", "第二十三周", "第二十四周", "第二十五周",
            "第二十六周", "第二十七周", "第二十八周", "第二十九周", "第三十周"
    };

    private final JdbcTemplate jdbc;

    public StatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> summary(LocalDate from, LocalDate to) {
        requireManager();
        Map<Long, Map<String, Object>> rows = new LinkedHashMap<>();
        jdbc.queryForList("""
                SELECT
                  r.user_id AS userId,
                  r.student_no_snapshot AS studentNo,
                  r.name_snapshot AS name,
                  u.phone AS phone,
                  u.major AS major,
                  u.grade AS grade,
                  u.qq AS qq,
                  COUNT(*) AS dutyCount,
                  COALESCE(SUM(r.valid_hours), 0) AS totalHours
                FROM attendance_records r
                JOIN users u ON u.id = r.user_id
                WHERE r.effective_status = 'VALID'
                  AND r.duty_date BETWEEN ? AND ?
                GROUP BY r.user_id, r.student_no_snapshot, r.name_snapshot, u.phone, u.major, u.grade, u.qq
                ORDER BY totalHours DESC, dutyCount DESC, r.student_no_snapshot
                """, from, to).forEach(row -> mergeSummaryRow(rows, row));
        jdbc.queryForList("""
                SELECT
                  p.user_id AS userId,
                  p.student_no_snapshot AS studentNo,
                  p.name_snapshot AS name,
                  u.phone AS phone,
                  u.major AS major,
                  u.grade AS grade,
                  u.qq AS qq,
                  COUNT(*) AS dutyCount,
                  COALESCE(SUM(p.duration_hours), 0) AS totalHours
                FROM training_participants p
                JOIN training_sessions s ON s.id = p.session_id
                JOIN users u ON u.id = p.user_id
                WHERE s.status <> 'ARCHIVED'
                  AND s.training_date BETWEEN ? AND ?
                  AND p.user_id IS NOT NULL
                  AND p.duration_hours > 0
                GROUP BY p.user_id, p.student_no_snapshot, p.name_snapshot, u.phone, u.major, u.grade, u.qq
                """, from, to).forEach(row -> mergeSummaryRow(rows, row));
        return rows.values().stream()
                .sorted(Comparator
                        .<Map<String, Object>>comparingDouble(row -> decimal(row.get("totalHours")).doubleValue())
                        .reversed()
                        .thenComparing(row -> -number(row.get("dutyCount")))
                        .thenComparing(row -> String.valueOf(row.get("studentNo"))))
                .toList();
    }

    public Map<String, Object> weeklyDetail(LocalDate from, LocalDate to) {
        requireManager();
        Map<Integer, String> dutyWeekdays = new HashMap<>();
        jdbc.queryForList("""
                SELECT weekday, weekday_name
                FROM duty_weekday_settings
                WHERE enabled = 1
                ORDER BY weekday
                """).forEach(row -> dutyWeekdays.put(
                ((Number) row.get("weekday")).intValue(),
                String.valueOf(row.get("weekday_name"))
        ));

        Set<LocalDate> activeDates = activeHourDates(from, to);
        List<Map<String, Object>> days = new java.util.ArrayList<>();
        Map<String, Map<String, BigDecimal>> cells = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            int weekday = date.getDayOfWeek().getValue();
            if (!dutyWeekdays.containsKey(weekday) && !activeDates.contains(date)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("dutyDate", date.toString());
            item.put("weekday", weekday);
            item.put("weekdayName", dutyWeekdays.getOrDefault(weekday, weekdayName(date)));
            days.add(item);
            cells.put(date.toString(), new LinkedHashMap<>());
        }

        Map<Long, Map<String, Object>> userRows = new LinkedHashMap<>();
        jdbc.queryForList("""
                SELECT
                  r.user_id AS userId,
                  r.student_no_snapshot AS studentNo,
                  r.name_snapshot AS name,
                  COALESCE(SUM(r.valid_hours), 0) AS totalHours
                FROM attendance_records r
                WHERE r.effective_status = 'VALID'
                  AND r.duty_date BETWEEN ? AND ?
                GROUP BY r.user_id, r.student_no_snapshot, r.name_snapshot
                ORDER BY totalHours DESC, r.student_no_snapshot
                """, from, to).forEach(row -> mergeSimpleUserRow(userRows, row));
        jdbc.queryForList("""
                SELECT
                  p.user_id AS userId,
                  p.student_no_snapshot AS studentNo,
                  p.name_snapshot AS name,
                  COALESCE(SUM(p.duration_hours), 0) AS totalHours
                FROM training_participants p
                JOIN training_sessions s ON s.id = p.session_id
                WHERE s.status <> 'ARCHIVED'
                  AND s.training_date BETWEEN ? AND ?
                  AND p.user_id IS NOT NULL
                  AND p.duration_hours > 0
                GROUP BY p.user_id, p.student_no_snapshot, p.name_snapshot
                """, from, to).forEach(row -> mergeSimpleUserRow(userRows, row));
        List<Map<String, Object>> users = userRows.values().stream()
                .sorted(Comparator
                        .<Map<String, Object>>comparingDouble(row -> decimal(row.get("totalHours")).doubleValue())
                        .reversed()
                        .thenComparing(row -> String.valueOf(row.get("studentNo"))))
                .toList();

        jdbc.queryForList("""
                SELECT
                  r.duty_date AS dutyDate,
                  r.user_id AS userId,
                  COALESCE(SUM(r.valid_hours), 0) AS totalHours
                FROM attendance_records r
                WHERE r.effective_status = 'VALID'
                  AND r.duty_date BETWEEN ? AND ?
                GROUP BY r.duty_date, r.user_id
                ORDER BY r.duty_date, r.user_id
                """, from, to).forEach(row -> {
            Object rawDate = row.get("dutyDate");
            LocalDate dutyDate = rawDate instanceof java.sql.Date sqlDate
                    ? sqlDate.toLocalDate()
                    : LocalDate.parse(String.valueOf(rawDate));
            Map<String, BigDecimal> rowCells = cells.get(dutyDate.toString());
            if (rowCells != null) {
                mergeCellHours(rowCells, row.get("userId"), row.get("totalHours"));
            }
        });
        jdbc.queryForList("""
                SELECT
                  s.training_date AS dutyDate,
                  p.user_id AS userId,
                  COALESCE(SUM(p.duration_hours), 0) AS totalHours
                FROM training_participants p
                JOIN training_sessions s ON s.id = p.session_id
                WHERE s.status <> 'ARCHIVED'
                  AND s.training_date BETWEEN ? AND ?
                  AND p.user_id IS NOT NULL
                  AND p.duration_hours > 0
                GROUP BY s.training_date, p.user_id
                ORDER BY s.training_date, p.user_id
                """, from, to).forEach(row -> {
            Object rawDate = row.get("dutyDate");
            LocalDate dutyDate = toLocalDate(rawDate);
            Map<String, BigDecimal> rowCells = cells.get(dutyDate.toString());
            if (rowCells != null) {
                mergeCellHours(rowCells, row.get("userId"), row.get("totalHours"));
            }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("users", users);
        result.put("cells", cells);
        return result;
    }

    public Map<String, Object> dashboard(LocalDate date) {
        requireManager();
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate yearStart = LocalDate.of(date.getYear(), 1, 1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayRecordCount", number("""
                SELECT COUNT(*)
                FROM attendance_records
                WHERE duty_date = ?
                """, date));
        result.put("todayOpenCount", number("""
                SELECT COUNT(*)
                FROM attendance_records
                WHERE duty_date = ?
                  AND check_out_time IS NULL
                  AND check_out_status = 'NOT_SUBMITTED'
                  AND check_in_status <> 'REJECTED'
                """, date));
        result.put("todayPendingCount", number("""
                SELECT COUNT(*)
                FROM attendance_records
                WHERE duty_date = ?
                  AND (check_in_status = 'PENDING' OR check_out_status = 'PENDING')
                """, date));
        result.put("ongoingRepairCount", number("""
                SELECT COUNT(*)
                FROM repair_cases
                WHERE deleted_at IS NULL
                  AND status IN ('RECEIVED', 'DIAGNOSING', 'REPAIRING', 'WAITING_PICKUP')
                """));
        result.put("todayValidHours", hoursForRange(date, date));
        result.put("weekValidHours", hoursForRange(weekStart, weekEnd));
        result.put("yearValidHours", hoursForRange(yearStart, date));
        result.put("yearValidCount", number("""
                SELECT COUNT(*)
                FROM attendance_records
                WHERE duty_date BETWEEN ? AND ?
                  AND effective_status = 'VALID'
                """, yearStart, date) + number("""
                SELECT COUNT(*)
                FROM training_participants p
                JOIN training_sessions s ON s.id = p.session_id
                WHERE s.status <> 'ARCHIVED'
                  AND s.training_date BETWEEN ? AND ?
                  AND p.user_id IS NOT NULL
                  AND p.duration_hours > 0
                """, yearStart, date));
        return result;
    }

    public byte[] export(LocalDate from, LocalDate to) {
        if (!AuthContext.current().role().canExport()) {
            throw ApiException.forbidden("无权导出 Excel");
        }
        if (from.isAfter(to)) {
            throw ApiException.badRequest("开始日期不能晚于结束日期");
        }

        List<ExportDay> dutyDays = exportDays(from, to);
        List<ExportUserRow> userRows = exportUserRows(from, to);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("值班记录");
            CellStyle weekStyle = weekHeaderStyle(wb);
            CellStyle headerStyle = headerStyle(wb);
            CellStyle textStyle = textStyle(wb);
            CellStyle numberStyle = numberStyle(wb);

            Row weekHeader = sheet.createRow(0);
            Row dayHeader = sheet.createRow(1);
            dayHeader.createCell(0).setCellValue("学号");
            dayHeader.createCell(1).setCellValue("姓名");
            dayHeader.getCell(0).setCellStyle(headerStyle);
            dayHeader.getCell(1).setCellStyle(headerStyle);

            int firstDayCol = 2;
            int totalCol = firstDayCol + dutyDays.size();
            createWeekHeaders(sheet, weekHeader, dutyDays, firstDayCol, weekStyle);
            for (int i = 0; i < dutyDays.size(); i++) {
                Cell cell = dayHeader.createCell(firstDayCol + i);
                cell.setCellValue(dutyDays.get(i).weekdayName() + "（" + EXPORT_DATE.format(dutyDays.get(i).date()) + "）");
                cell.setCellStyle(headerStyle);
            }
            Cell totalHeader = dayHeader.createCell(totalCol);
            totalHeader.setCellValue("有效总时长");
            totalHeader.setCellStyle(headerStyle);

            for (int i = 0; i < userRows.size(); i++) {
                Row row = sheet.createRow(i + 2);
                ExportUserRow item = userRows.get(i);

                Cell studentNoCell = row.createCell(0);
                studentNoCell.setCellValue(item.studentNo());
                studentNoCell.setCellStyle(textStyle);

                Cell nameCell = row.createCell(1);
                nameCell.setCellValue(item.name());
                nameCell.setCellStyle(textStyle);

                for (int j = 0; j < dutyDays.size(); j++) {
                    Cell cell = row.createCell(firstDayCol + j);
                    BigDecimal hours = item.hoursByDate().get(dutyDays.get(j).date());
                    if (hours != null && hours.compareTo(BigDecimal.ZERO) > 0) {
                        cell.setCellValue(hours.doubleValue());
                    }
                    cell.setCellStyle(numberStyle);
                }

                Cell totalCell = row.createCell(totalCol);
                if (dutyDays.isEmpty()) {
                    totalCell.setCellValue(0);
                } else {
                    int excelRow = row.getRowNum() + 1;
                    String start = CellReference.convertNumToColString(firstDayCol) + excelRow;
                    String end = CellReference.convertNumToColString(totalCol - 1) + excelRow;
                    totalCell.setCellFormula("SUM(" + start + ":" + end + ")");
                }
                totalCell.setCellStyle(numberStyle);
            }

            sheet.createFreezePane(2, 2);
            for (int i = 0; i <= totalCol; i++) {
                sheet.autoSizeColumn(i);
                int width = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(Math.max(width + 512, 10 * 256), 22 * 256));
            }

            wb.setForceFormulaRecalculation(true);
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw ApiException.badRequest("导出 Excel 失败");
        }
    }

    private List<ExportDay> exportDays(LocalDate from, LocalDate to) {
        Map<Integer, String> enabledWeekdays = new HashMap<>();
        jdbc.queryForList("""
                SELECT weekday, weekday_name
                FROM duty_weekday_settings
                WHERE enabled = 1
                ORDER BY weekday
                """).forEach(row -> enabledWeekdays.put(
                ((Number) row.get("weekday")).intValue(),
                String.valueOf(row.get("weekday_name"))
        ));

        Set<LocalDate> activeDates = activeHourDates(from, to);

        List<ExportDay> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            int weekday = date.getDayOfWeek().getValue();
            if (!enabledWeekdays.containsKey(weekday) && !activeDates.contains(date)) {
                continue;
            }
            String weekdayName = enabledWeekdays.getOrDefault(weekday, weekdayName(date));
            LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            days.add(new ExportDay(date, weekdayName, weekStart));
        }
        return days;
    }

    private List<ExportUserRow> exportUserRows(LocalDate from, LocalDate to) {
        Map<Long, ExportUserRow> users = new LinkedHashMap<>();
        jdbc.queryForList("""
                SELECT
                  r.user_id AS userId,
                  r.student_no_snapshot AS studentNo,
                  r.name_snapshot AS name,
                  r.duty_date AS dutyDate,
                  COALESCE(SUM(r.valid_hours), 0) AS totalHours
                FROM attendance_records r
                WHERE r.effective_status = 'VALID'
                  AND r.duty_date BETWEEN ? AND ?
                GROUP BY r.user_id, r.student_no_snapshot, r.name_snapshot, r.duty_date
                ORDER BY r.student_no_snapshot, r.duty_date
                """, from, to).forEach(row -> {
            long userId = ((Number) row.get("userId")).longValue();
            ExportUserRow user = users.computeIfAbsent(userId, id -> new ExportUserRow(
                    String.valueOf(row.get("studentNo")),
                    String.valueOf(row.get("name")),
                    new LinkedHashMap<>()
            ));
            mergeExportHours(user.hoursByDate(), row.get("dutyDate"), row.get("totalHours"));
        });
        jdbc.queryForList("""
                SELECT
                  p.user_id AS userId,
                  p.student_no_snapshot AS studentNo,
                  p.name_snapshot AS name,
                  s.training_date AS dutyDate,
                  COALESCE(SUM(p.duration_hours), 0) AS totalHours
                FROM training_participants p
                JOIN training_sessions s ON s.id = p.session_id
                WHERE s.status <> 'ARCHIVED'
                  AND s.training_date BETWEEN ? AND ?
                  AND p.user_id IS NOT NULL
                  AND p.duration_hours > 0
                GROUP BY p.user_id, p.student_no_snapshot, p.name_snapshot, s.training_date
                ORDER BY p.student_no_snapshot, s.training_date
                """, from, to).forEach(row -> {
            long userId = ((Number) row.get("userId")).longValue();
            ExportUserRow user = users.computeIfAbsent(userId, id -> new ExportUserRow(
                    String.valueOf(row.get("studentNo")),
                    String.valueOf(row.get("name")),
                    new LinkedHashMap<>()
            ));
            mergeExportHours(user.hoursByDate(), row.get("dutyDate"), row.get("totalHours"));
        });
        return new ArrayList<>(users.values());
    }

    private Set<LocalDate> activeHourDates(LocalDate from, LocalDate to) {
        Set<LocalDate> dates = new HashSet<>();
        jdbc.queryForList("""
                SELECT DISTINCT duty_date AS dutyDate
                FROM attendance_records
                WHERE effective_status = 'VALID'
                  AND duty_date BETWEEN ? AND ?
                """, from, to).forEach(row -> dates.add(toLocalDate(row.get("dutyDate"))));
        jdbc.queryForList("""
                SELECT DISTINCT s.training_date AS dutyDate
                FROM training_participants p
                JOIN training_sessions s ON s.id = p.session_id
                WHERE s.status <> 'ARCHIVED'
                  AND s.training_date BETWEEN ? AND ?
                  AND p.user_id IS NOT NULL
                  AND p.duration_hours > 0
                """, from, to).forEach(row -> dates.add(toLocalDate(row.get("dutyDate"))));
        return dates;
    }

    private BigDecimal hoursForRange(LocalDate from, LocalDate to) {
        BigDecimal attendanceHours = decimal(jdbc.queryForObject("""
                SELECT COALESCE(SUM(valid_hours), 0)
                FROM attendance_records
                WHERE duty_date BETWEEN ? AND ?
                  AND effective_status = 'VALID'
                """, Number.class, from, to));
        BigDecimal trainingHours = decimal(jdbc.queryForObject("""
                SELECT COALESCE(SUM(p.duration_hours), 0)
                FROM training_participants p
                JOIN training_sessions s ON s.id = p.session_id
                WHERE s.status <> 'ARCHIVED'
                  AND s.training_date BETWEEN ? AND ?
                  AND p.user_id IS NOT NULL
                  AND p.duration_hours > 0
                """, Number.class, from, to));
        return attendanceHours.add(trainingHours).setScale(2, RoundingMode.HALF_UP);
    }

    private void mergeSummaryRow(Map<Long, Map<String, Object>> rows, Map<String, Object> row) {
        long userId = ((Number) row.get("userId")).longValue();
        Map<String, Object> merged = rows.computeIfAbsent(userId, id -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", userId);
            item.put("studentNo", row.get("studentNo"));
            item.put("name", row.get("name"));
            item.put("phone", row.get("phone"));
            item.put("major", row.get("major"));
            item.put("grade", row.get("grade"));
            item.put("qq", row.get("qq"));
            item.put("dutyCount", 0);
            item.put("totalHours", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            return item;
        });
        merged.put("dutyCount", number(merged.get("dutyCount")) + number(row.get("dutyCount")));
        merged.put("totalHours", decimal(merged.get("totalHours")).add(decimal(row.get("totalHours"))).setScale(2, RoundingMode.HALF_UP));
    }

    private void mergeSimpleUserRow(Map<Long, Map<String, Object>> rows, Map<String, Object> row) {
        long userId = ((Number) row.get("userId")).longValue();
        Map<String, Object> merged = rows.computeIfAbsent(userId, id -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", userId);
            item.put("studentNo", row.get("studentNo"));
            item.put("name", row.get("name"));
            item.put("totalHours", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            return item;
        });
        merged.put("totalHours", decimal(merged.get("totalHours")).add(decimal(row.get("totalHours"))).setScale(2, RoundingMode.HALF_UP));
    }

    private void mergeCellHours(Map<String, BigDecimal> rowCells, Object rawUserId, Object rawHours) {
        String userId = String.valueOf(((Number) rawUserId).longValue());
        rowCells.merge(userId, decimal(rawHours), (left, right) -> left.add(right).setScale(2, RoundingMode.HALF_UP));
    }

    private void mergeExportHours(Map<LocalDate, BigDecimal> hoursByDate, Object rawDate, Object rawHours) {
        LocalDate date = toLocalDate(rawDate);
        hoursByDate.merge(date, decimal(rawHours), (left, right) -> left.add(right).setScale(2, RoundingMode.HALF_UP));
    }

    private void createWeekHeaders(Sheet sheet, Row row, List<ExportDay> days, int firstDayCol, CellStyle style) {
        int weekIndex = 1;
        for (int i = 0; i < days.size(); ) {
            LocalDate weekStart = days.get(i).weekStart();
            int startIndex = i;
            while (i < days.size() && days.get(i).weekStart().equals(weekStart)) {
                i++;
            }
            int startCol = firstDayCol + startIndex;
            int endCol = firstDayCol + i - 1;
            Cell cell = row.createCell(startCol);
            cell.setCellValue(weekTitle(weekIndex, days.get(startIndex).date(), days.get(i - 1).date()));
            cell.setCellStyle(style);
            if (startCol < endCol) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, startCol, endCol));
                for (int col = startCol + 1; col <= endCol; col++) {
                    row.createCell(col).setCellStyle(style);
                }
            }
            weekIndex++;
        }
    }

    private String weekTitle(int index, LocalDate firstDate, LocalDate lastDate) {
        String label = index <= WEEK_LABELS.length ? WEEK_LABELS[index - 1] : "第" + index + "周";
        return label + "（" + EXPORT_DATE.format(firstDate) + "-" + EXPORT_DATE.format(lastDate) + "）";
    }

    private String weekdayName(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }

    private LocalDate toLocalDate(Object rawDate) {
        if (rawDate instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(rawDate));
    }

    private CellStyle weekHeaderStyle(Workbook wb) {
        CellStyle style = borderedStyle(wb);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = wb.createFont();
        font.setFontName("Microsoft YaHei");
        font.setBold(true);
        style.setFont(font);
        return style;
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
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = wb.createFont();
        font.setFontName("Microsoft YaHei");
        style.setFont(font);
        return style;
    }

    private CellStyle numberStyle(Workbook wb) {
        CellStyle style = textStyle(wb);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setDataFormat(wb.createDataFormat().getFormat("0.##"));
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

    private void requireManager() {
        if (!AuthContext.current().role().atLeastManager()) {
            throw ApiException.forbidden("无权查看统计");
        }
    }

    private int number(String sql, Object... args) {
        Number value = jdbc.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
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
                // fall through
            }
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private record ExportDay(LocalDate date, String weekdayName, LocalDate weekStart) {
    }

    private record ExportUserRow(String studentNo, String name, Map<LocalDate, BigDecimal> hoursByDate) {
    }
}
