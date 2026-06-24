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
        return jdbc.queryForList("""
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
                """, from, to);
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

        List<Map<String, Object>> days = new java.util.ArrayList<>();
        Map<String, Map<String, Integer>> cells = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            int weekday = date.getDayOfWeek().getValue();
            if (!dutyWeekdays.containsKey(weekday)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("dutyDate", date.toString());
            item.put("weekday", weekday);
            item.put("weekdayName", dutyWeekdays.get(weekday));
            days.add(item);
            cells.put(date.toString(), new LinkedHashMap<>());
        }

        List<Map<String, Object>> users = jdbc.queryForList("""
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
                """, from, to);

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
            Map<String, Integer> rowCells = cells.get(dutyDate.toString());
            if (rowCells != null) {
                rowCells.put(
                        String.valueOf(((Number) row.get("userId")).longValue()),
                        ((Number) row.get("totalHours")).intValue()
                );
            }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("users", users);
        result.put("cells", cells);
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
                    Integer hours = item.hoursByDate().get(dutyDays.get(j).date());
                    if (hours != null && hours > 0) {
                        cell.setCellValue(hours);
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

        Set<LocalDate> validRecordDates = new HashSet<>();
        jdbc.queryForList("""
                SELECT DISTINCT duty_date AS dutyDate
                FROM attendance_records
                WHERE effective_status = 'VALID'
                  AND duty_date BETWEEN ? AND ?
                ORDER BY duty_date
                """, from, to).forEach(row -> validRecordDates.add(toLocalDate(row.get("dutyDate"))));

        List<ExportDay> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            int weekday = date.getDayOfWeek().getValue();
            if (!enabledWeekdays.containsKey(weekday) && !validRecordDates.contains(date)) {
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
            user.hoursByDate().put(toLocalDate(row.get("dutyDate")), ((Number) row.get("totalHours")).intValue());
        });
        return new ArrayList<>(users.values());
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
        style.setDataFormat(wb.createDataFormat().getFormat("0"));
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

    private record ExportDay(LocalDate date, String weekdayName, LocalDate weekStart) {
    }

    private record ExportUserRow(String studentNo, String name, Map<LocalDate, Integer> hoursByDate) {
    }
}
