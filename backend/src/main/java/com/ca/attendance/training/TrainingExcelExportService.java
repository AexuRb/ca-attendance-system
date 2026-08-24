package com.ca.attendance.training;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.DownloadFilename;
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
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TrainingExcelExportService {
    ExportDocument generateImportTemplate(TrainingSessionItem session, BigDecimal defaultDuration) {
        String filename = session == null
                ? "培训名单导入模板.xlsx"
                : "培训名单导入模板_" + DownloadFilename.stem(session.title(), "培训") + "_" + session.trainingDate() + ".xlsx";
        return generate(filename, workbook -> writeImportTemplateWorkbook(workbook, session, defaultDuration));
    }

    ExportDocument generateSession(TrainingSessionItem session, List<TrainingParticipantItem> rows) {
        String filename = "培训名单_" + DownloadFilename.stem(session.title(), "培训") + "_" + session.trainingDate() + ".xlsx";
        return generate(filename, workbook -> writeSessionWorkbook(workbook, session, rows));
    }

    ExportDocument generateSummary(List<TrainingSessionItem> sessions, List<Map<String, Object>> memberRows,
                                   LocalDate start, LocalDate end) {
        String filename = "培训统计_" + start + "_" + end + ".xlsx";
        return generate(filename, workbook -> writeSummaryWorkbook(workbook, sessions, memberRows, start, end));
    }

    private ExportDocument generate(String filename, WorkbookWriter writer) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        workbook.setCompressTempFiles(true);
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writer.write(workbook);
            workbook.write(output);
            return new ExportDocument(filename, output.toByteArray());
        } catch (Exception ex) {
            throw ApiException.badRequest("生成 Excel 失败");
        } finally {
            workbook.dispose();
        }
    }

    private void writeImportTemplateWorkbook(Workbook workbook, TrainingSessionItem session,
                                             BigDecimal defaultDuration) {
        CellStyle titleStyle = titleStyle(workbook);
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle textStyle = textStyle(workbook);
        CellStyle studentNoStyle = textStyle(workbook);
        studentNoStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));

        Sheet dataSheet = workbook.createSheet("参与名单");
        dataSheet.setDefaultColumnStyle(0, studentNoStyle);
        String[] headers = {"学号", "姓名", "时长", "备注"};
        Row header = dataSheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(headerStyle);
        }
        if (session != null && session.speaker() != null && !session.speaker().isBlank()) {
            Row speaker = dataSheet.createRow(1);
            speaker.createCell(0).setCellValue("");
            speaker.createCell(1).setCellValue(session.speaker());
            speaker.createCell(2).setCellValue(defaultDuration.doubleValue());
            speaker.createCell(3).setCellValue("主讲人");
            for (int index = 0; index < headers.length; index++) {
                speaker.getCell(index).setCellStyle(index == 0 ? studentNoStyle : textStyle);
            }
        }
        setColumnWidths(dataSheet, 18, 18, 12, 28);
        dataSheet.createFreezePane(0, 1);

        Sheet noteSheet = workbook.createSheet("填写说明");
        Row title = noteSheet.createRow(0);
        title.createCell(0).setCellValue("培训参与名单导入模板");
        title.getCell(0).setCellStyle(titleStyle);
        List<String> notes = new ArrayList<>();
        if (session == null) {
            notes.add("通用模板：请在培训管理中选择具体培训后导入。");
        } else {
            notes.add("培训：" + session.title() + "（" + session.trainingDate() + "）");
            notes.add("主讲人：" + valueOrDash(session.speaker()));
            notes.add("默认时长：" + defaultDuration.stripTrailingZeros().toPlainString() + " 小时");
        }
        notes.add("参与名单工作表第一行为表头，请从第二行开始填写。");
        notes.add("必填列：姓名。建议同时填写学号，避免同名成员无法匹配。");
        notes.add("时长可不填；不填时导入会使用该培训的开始/结束时间。");
        notes.add("时长会计入值班时长，可填写 1、1.5、2 或 2小时。");
        notes.add("第一条数据建议填写主讲人；当前培训已填写主讲人时会自动预填。");
        for (int index = 0; index < notes.size(); index++) {
            Row row = noteSheet.createRow(index + 2);
            Cell cell = row.createCell(0);
            cell.setCellValue(notes.get(index));
            cell.setCellStyle(textStyle);
        }
        noteSheet.setColumnWidth(0, 58 * 256);
        workbook.setActiveSheet(0);
    }

    private void writeSessionWorkbook(Workbook workbook, TrainingSessionItem session,
                                      List<TrainingParticipantItem> rows) {
        Sheet sheet = workbook.createSheet("培训名单");
        CellStyle titleStyle = titleStyle(workbook);
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle textStyle = textStyle(workbook);

        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue(session.title());
        title.getCell(0).setCellStyle(titleStyle);
        Row meta = sheet.createRow(1);
        meta.createCell(0).setCellValue("日期");
        meta.createCell(1).setCellValue(String.valueOf(session.trainingDate()));
        meta.createCell(2).setCellValue("地点");
        meta.createCell(3).setCellValue(valueOrDash(session.location()));
        meta.createCell(4).setCellValue("主讲人");
        meta.createCell(5).setCellValue(valueOrDash(session.speaker()));

        String[] headers = {"序号", "学号", "姓名", "时长", "备注"};
        Row header = sheet.createRow(3);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(headerStyle);
        }
        for (int index = 0; index < rows.size(); index++) {
            TrainingParticipantItem item = rows.get(index);
            Row row = sheet.createRow(index + 4);
            row.createCell(0).setCellValue(index + 1);
            row.createCell(1).setCellValue(item.studentNo());
            row.createCell(2).setCellValue(item.name());
            row.createCell(3).setCellValue(item.durationHours().doubleValue());
            row.createCell(4).setCellValue(valueOrDash(item.remark()));
            for (int column = 0; column < headers.length; column++) {
                row.getCell(column).setCellStyle(textStyle);
            }
        }
        finishSheet(sheet, 8, 18, 16, 12, 32);
    }

    private void writeSummaryWorkbook(Workbook workbook, List<TrainingSessionItem> sessions,
                                      List<Map<String, Object>> memberRows, LocalDate start, LocalDate end) {
        CellStyle titleStyle = titleStyle(workbook);
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle textStyle = textStyle(workbook);

        Sheet sessionSheet = workbook.createSheet("培训场次");
        Row title = sessionSheet.createRow(0);
        title.createCell(0).setCellValue("培训统计 " + start + " 至 " + end);
        title.getCell(0).setCellStyle(titleStyle);
        String[] sessionHeaders = {"日期", "标题", "地点", "主讲人", "参与", "培训时长"};
        Row header = sessionSheet.createRow(2);
        for (int index = 0; index < sessionHeaders.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(sessionHeaders[index]);
            cell.setCellStyle(headerStyle);
        }
        for (int index = 0; index < sessions.size(); index++) {
            TrainingSessionItem item = sessions.get(index);
            Row row = sessionSheet.createRow(index + 3);
            row.createCell(0).setCellValue(String.valueOf(item.trainingDate()));
            row.createCell(1).setCellValue(item.title());
            row.createCell(2).setCellValue(valueOrDash(item.location()));
            row.createCell(3).setCellValue(valueOrDash(item.speaker()));
            row.createCell(4).setCellValue(item.participantCount());
            row.createCell(5).setCellValue(item.totalDurationHours().doubleValue());
            for (int column = 0; column < sessionHeaders.length; column++) {
                row.getCell(column).setCellStyle(textStyle);
            }
        }
        finishSheet(sessionSheet, 14, 28, 20, 18, 10, 14);

        Sheet memberSheet = workbook.createSheet("成员统计");
        String[] memberHeaders = {"学号", "姓名", "参加次数", "培训时长"};
        Row memberHeader = memberSheet.createRow(0);
        for (int index = 0; index < memberHeaders.length; index++) {
            Cell cell = memberHeader.createCell(index);
            cell.setCellValue(memberHeaders[index]);
            cell.setCellStyle(headerStyle);
        }
        for (int index = 0; index < memberRows.size(); index++) {
            Map<String, Object> item = memberRows.get(index);
            Row row = memberSheet.createRow(index + 1);
            row.createCell(0).setCellValue(String.valueOf(item.get("studentNo")));
            row.createCell(1).setCellValue(String.valueOf(item.get("name")));
            row.createCell(2).setCellValue(number(item.get("trainingCount")));
            row.createCell(3).setCellValue(decimal(item.get("durationHours")).doubleValue());
            for (int column = 0; column < memberHeaders.length; column++) {
                row.getCell(column).setCellStyle(textStyle);
            }
        }
        finishSheet(memberSheet, 18, 16, 12, 14);
    }

    private CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Microsoft YaHei");
        font.setFontHeightInPoints((short) 14);
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = borderedStyle(workbook);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setFontName("Microsoft YaHei");
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle textStyle(Workbook workbook) {
        CellStyle style = borderedStyle(workbook);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
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

    private void finishSheet(Sheet sheet, int... widths) {
        sheet.createFreezePane(0, Math.min(3, sheet.getLastRowNum()));
        setColumnWidths(sheet, widths);
    }

    private void setColumnWidths(Sheet sheet, int... widths) {
        for (int index = 0; index < widths.length; index++) {
            sheet.setColumnWidth(index, widths[index] * 256);
        }
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
                // Fall through to zero for an unexpected aggregate value.
            }
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    record ExportDocument(String filename, byte[] bytes) {
    }

    private interface WorkbookWriter {
        void write(Workbook workbook);
    }
}
