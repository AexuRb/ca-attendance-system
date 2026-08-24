package com.ca.attendance.repair;

import com.ca.attendance.common.ApiException;
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
import java.time.LocalDate;
import java.util.List;

@Service
public class RepairExcelExportService {
    private static final String[] HEADERS = {
            "编号", "状态", "协议类型", "接收时间", "完成时间", "送修人", "联系方式",
            "设备类型", "品牌", "型号", "随附物品", "故障描述", "处理记录",
            "数据备份提醒", "风险确认", "隐私提示", "处理人", "备注"
    };
    private static final int[] COLUMN_WIDTHS = {
            18, 12, 14, 21, 21, 14, 16, 14, 14, 16, 22, 34, 34, 14, 12, 12, 16, 28
    };

    ExportDocument generate(List<RepairCaseItem> rows, LocalDate start, LocalDate end) {
        String filename = "维修事务_" + start + "_" + end + ".xlsx";
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        workbook.setCompressTempFiles(true);
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeWorkbook(workbook, rows, start, end);
            workbook.write(output);
            return new ExportDocument(filename, output.toByteArray());
        } catch (Exception ex) {
            throw ApiException.badRequest("生成 Excel 失败");
        } finally {
            workbook.dispose();
        }
    }

    private void writeWorkbook(Workbook workbook, List<RepairCaseItem> rows, LocalDate start, LocalDate end) {
        Sheet sheet = workbook.createSheet("维修事务");
        CellStyle titleStyle = titleStyle(workbook);
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle textStyle = textStyle(workbook);

        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue("维修事务 " + start + " 至 " + end);
        title.getCell(0).setCellStyle(titleStyle);

        Row header = sheet.createRow(2);
        for (int index = 0; index < HEADERS.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(HEADERS[index]);
            cell.setCellStyle(headerStyle);
        }

        for (int index = 0; index < rows.size(); index++) {
            writeRow(sheet.createRow(index + 3), rows.get(index), textStyle);
        }

        sheet.createFreezePane(0, 3);
        for (int index = 0; index < HEADERS.length; index++) {
            sheet.setColumnWidth(index, COLUMN_WIDTHS[index] * 256);
        }
    }

    private void writeRow(Row row, RepairCaseItem item, CellStyle textStyle) {
        row.createCell(0).setCellValue(item.caseNo());
        row.createCell(1).setCellValue(RepairDisplayText.status(item.status()));
        row.createCell(2).setCellValue(RepairDisplayText.agreementType(item.agreementType()));
        row.createCell(3).setCellValue(RepairDisplayText.time(item.receivedAt()));
        row.createCell(4).setCellValue(RepairDisplayText.time(item.completedAt()));
        row.createCell(5).setCellValue(item.ownerName());
        row.createCell(6).setCellValue(RepairDisplayText.valueOrDash(item.ownerPhone()));
        row.createCell(7).setCellValue(item.deviceType());
        row.createCell(8).setCellValue(RepairDisplayText.valueOrDash(item.deviceBrand()));
        row.createCell(9).setCellValue(RepairDisplayText.valueOrDash(item.deviceModel()));
        row.createCell(10).setCellValue(RepairDisplayText.valueOrDash(item.accessories()));
        row.createCell(11).setCellValue(item.faultDescription());
        row.createCell(12).setCellValue(RepairDisplayText.valueOrDash(item.serviceDescription()));
        row.createCell(13).setCellValue(item.dataBackupConfirmed() ? "是" : "否");
        row.createCell(14).setCellValue(item.riskAcknowledged() ? "是" : "否");
        row.createCell(15).setCellValue(item.privacyAcknowledged() ? "是" : "否");
        row.createCell(16).setCellValue(RepairDisplayText.valueOrDash(item.handlerName()));
        row.createCell(17).setCellValue(RepairDisplayText.valueOrDash(item.remark()));
        for (int index = 0; index < HEADERS.length; index++) {
            row.getCell(index).setCellStyle(textStyle);
        }
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

    record ExportDocument(String filename, byte[] bytes) {
    }
}
