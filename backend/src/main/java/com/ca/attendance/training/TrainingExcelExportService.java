package com.ca.attendance.training;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.DownloadFilename;
import com.ca.attendance.common.ExcelImportTemplateGuide;
import com.ca.attendance.common.ExcelImportTemplateGuide.Item;
import com.ca.attendance.common.ExcelImportTemplateGuide.Section;
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
        CellStyle headerStyle = headerStyle(workbook);
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
        setColumnWidths(dataSheet, 36, 18, 12, 28);
        dataSheet.createFreezePane(0, 1);
        ExcelImportTemplateGuide.fitPrintWidth(dataSheet, false);

        String target = session == null ? "通用模板。先在数据中心选择具体培训，再导入参与名单。"
                : "目标培训：" + session.title() + "（" + session.trainingDate()
                + "）。下载时默认时长：" + defaultDuration.stripTrailingZeros().toPlainString() + " 小时。";
        ExcelImportTemplateGuide.write(workbook, "培训参与名单导入模板", target, List.of(
                new Section("一、填写规则", List.of(
                        new Item("填写位置", "第一张表", "在“参与名单”第2行起填写，一行一人。保留第1行表头，不添加标题或说明行。"),
                        new Item("学号", "条件必填", "1—32位数字，按文本填写。留空时，姓名必须唯一匹配已有成员；外部人员、重名或无法匹配的主讲人必须补填学号。"),
                        new Item("姓名", "必填", "填写实际参与人的姓名。填写已有学号时，最终姓名以系统成员资料为准。"),
                        new Item("时长", "可留空", "单位：小时。可填1.5、2小时等，接受h/H/小时/时后缀；范围0—999.99，四舍五入保留两位小数。时长计入培训和合计时长。"),
                        new Item("备注", "可选", "填写本次培训的参与备注。更新已有记录时，留空会清除原备注。")
                )),
                new Section("二、导入后会发生什么", List.of(
                        new Item("已有参与记录", "更新，不累加", "同一培训中已有学号更新姓名、时长和备注。文件未包含的参与记录保持不变。"),
                        new Item("未建档学号", "外部参与记录", "保存到本次培训，不创建成员账号。"),
                        new Item("时长留空", "按目标培训计算", "采用导入时目标培训的起止时间；没有有效起止时间则为0。已填数字不会随培训时间变化自动更新。"),
                        new Item("参与人员", "按实际名单填写", "首表不预填任何人员。需要登记主讲人的参与记录时，请自行填写其学号、姓名和时长。"),
                        new Item("重复或其他错误", "整批不写入", "同一文件学号不能重复；学号留空时姓名不能重复。任意校验错误都需修正后重新校验。")
                )),
                new Section("三、导入前核对", List.of(
                        new Item("目标培训", "核对标题和日期", "先选择培训，再下载对应模板。培训时间可能变化，导入前重新核对目标培训和时长。"),
                        new Item("粘贴方式", "仅粘贴值", "学号列按文本填写，保留前导零和全部数字。带格式粘贴可能覆盖文本格式，已丢失的数字需从原始资料重新填写。"),
                        new Item("文件限制", "5 MB / 3000行", "填写范围为首表第2—3001行，中间空白行也占行号范围。删除范围外多余行。"),
                        new Item("操作流程", "校验后确认", "选择文件并校验，核对人员、时长和更新结果后确认导入。文件或目标变化后重新校验。"),
                        new Item("填写示例", "仅供参照", "示例表使用虚构姓名，系统不读取该表。请勿直接把示例当作实际人员导入。")
                ))
        ));
        ExcelImportTemplateGuide.examples(workbook, headers, List.of(
                List.of("000001", "示例成员甲", "1.5", "参与备注示例"),
                List.of("00123456789012345678901234567890", "示例外部人员乙", "2小时", "")
        ), "姓名和学号示例均为虚构，学号保留前导零。时长可留空按目标培训计算；备注留空在更新时会清除原备注。");
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
