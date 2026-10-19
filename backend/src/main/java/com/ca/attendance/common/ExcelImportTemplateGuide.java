package com.ca.attendance.common;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;

import java.util.List;

/** Reader-facing instructions and examples, kept outside the first import sheet. */
public final class ExcelImportTemplateGuide {
    private ExcelImportTemplateGuide() {}

    public record Item(String subject, String requirement, String description) {}
    public record Section(String title, List<Item> items) {}

    public static void write(Workbook workbook, String title, String context, List<Section> sections) {
        Sheet sheet = workbook.createSheet("填写说明");
        sheet.setDisplayGridlines(false);
        int[] widths = {20, 18, 76};
        for (int col = 0; col < widths.length; col++) sheet.setColumnWidth(col, widths[col] * 256);
        CellStyle body = style(workbook, false, false);
        CellStyle heading = style(workbook, true, true);
        CellStyle titleStyle = style(workbook, true, false);
        Font titleFont = workbook.createFont();
        titleFont.setFontName("Microsoft YaHei");
        titleFont.setFontHeightInPoints((short) 16);
        titleFont.setBold(true);
        titleStyle.setFont(titleFont);
        mergedLine(sheet, 0, title, titleStyle, 32);
        mergedLine(sheet, 1, context, body, Math.max(36, lines(context, 105) * 16 + 12));
        int rowIndex = 3;
        int sectionIndex = 0;
        for (Section section : sections) {
            // Keep the fill-in instructions on the first printed page and the effects together after it.
            if (sectionIndex++ == 1) sheet.setRowBreak(rowIndex - 1);
            mergedLine(sheet, rowIndex++, section.title(), heading, 28);
            for (Item item : section.items()) {
                Row row = sheet.createRow(rowIndex++);
                String[] values = {item.subject(), item.requirement(), item.description()};
                int lineCount = 1;
                for (int col = 0; col < values.length; col++) {
                    Cell cell = row.createCell(col);
                    cell.setCellValue(values[col]);
                    cell.setCellStyle(body);
                    lineCount = Math.max(lineCount, lines(values[col], widths[col] - 4));
                }
                row.setHeightInPoints(Math.max(30, lineCount * 16 + 12));
            }
            rowIndex++;
        }
        fitPrintWidth(sheet, false);
        workbook.setPrintArea(workbook.getSheetIndex(sheet), 0, 2, 0, rowIndex - 2);
    }

    public static void examples(Workbook workbook, String[] headers, List<List<String>> rows, String note) {
        Sheet sheet = workbook.createSheet("填写示例");
        sheet.setDisplayGridlines(false);
        CellStyle body = style(workbook, false, false);
        body.setDataFormat(workbook.createDataFormat().getFormat("@"));
        CellStyle heading = style(workbook, true, true);
        heading.setAlignment(HorizontalAlignment.CENTER);
        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue("虚构示例，仅供参照；请在第一张工作表填写实际数据。");
        title.getCell(0).setCellStyle(body);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));
        title.setHeightInPoints(36);
        Row header = sheet.createRow(1);
        for (int col = 0; col < headers.length; col++) {
            header.createCell(col).setCellValue(headers[col]);
            header.getCell(col).setCellStyle(heading);
            sheet.setColumnWidth(col, (headers[col].equals("学号") ? 42 : 22) * 256);
        }
        header.setHeightInPoints(28);
        for (int index = 0; index < rows.size(); index++) {
            Row row = sheet.createRow(index + 2);
            for (int col = 0; col < headers.length; col++) {
                row.createCell(col).setCellValue(rows.get(index).get(col));
                row.getCell(col).setCellStyle(body);
            }
            row.setHeightInPoints(32);
        }
        int noteIndex = rows.size() + 3;
        Row explanation = sheet.createRow(noteIndex);
        explanation.createCell(0).setCellValue(note);
        explanation.getCell(0).setCellStyle(body);
        sheet.addMergedRegion(new CellRangeAddress(noteIndex, noteIndex, 0, headers.length - 1));
        explanation.setHeightInPoints(Math.max(48, lines(note, 95) * 16 + 12));
        sheet.createFreezePane(0, 2);
        fitPrintWidth(sheet, true);
    }

    public static void fitPrintWidth(Sheet sheet, boolean onePage) {
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) (onePage ? 1 : 0));
        sheet.setFitToPage(true);
    }

    private static void mergedLine(Sheet sheet, int index, String value, CellStyle style, int height) {
        Row row = sheet.createRow(index);
        for (int col = 0; col < 3; col++) row.createCell(col).setCellStyle(style);
        row.getCell(0).setCellValue(value);
        sheet.addMergedRegion(new CellRangeAddress(index, index, 0, 2));
        row.setHeightInPoints(height);
    }

    private static int lines(String text, int width) {
        return (int) Math.ceil(text.codePoints().map(c -> c > 255 ? 2 : 1).sum() / (double) width);
    }

    private static CellStyle style(Workbook workbook, boolean bold, boolean filled) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setFontName("Microsoft YaHei");
        font.setFontHeightInPoints((short) 11);
        font.setBold(bold);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        if (!bold) font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        if (filled) {
            style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return style;
    }
}
