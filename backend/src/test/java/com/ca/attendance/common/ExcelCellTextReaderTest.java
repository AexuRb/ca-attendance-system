package com.ca.attendance.common;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelCellTextReaderTest {
    @Test void preservesTwentyDigitTextAndRejectsNumericPrecisionLoss() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Row row = workbook.createSheet().createRow(0);
            row.createCell(0).setCellValue("12345678901234567890");
            row.createCell(1).setCellValue(1.2345678901234567E19);
            row.createCell(2).setCellFormula("12345678901234567890");
            row.createCell(3).setCellFormula("\"12345678901234567890\"");
            ExcelCellTextReader reader = new ExcelCellTextReader(workbook);
            assertEquals("12345678901234567890", reader.readExactIdentifier(row,0,"QQ"));
            assertEquals("12345678901234567890", reader.readExactIdentifier(row,3,"QQ"));
            org.junit.jupiter.api.Assertions.assertThrows(ApiException.class,()->reader.readExactIdentifier(row,1,"QQ"));
            org.junit.jupiter.api.Assertions.assertThrows(ApiException.class,()->reader.readExactIdentifier(row,2,"QQ"));
        }
    }
    @Test
    void readsNumericAndTextCellsAsTrimmedPlainText() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Row row = workbook.createSheet().createRow(0);
            row.createCell(0).setCellValue(202301012345d);
            row.createCell(1).setCellValue(12.3400d);
            row.createCell(2).setCellValue(" 001230 ");
            row.createCell(3).setCellValue(true);

            ExcelCellTextReader reader = new ExcelCellTextReader(workbook);

            assertEquals("202301012345", reader.read(row.getCell(0)));
            assertEquals("12.34", reader.read(row, 1));
            assertEquals("001230", reader.read(row, 2));
            assertEquals("true", reader.read(row, 3));
        }
    }

    @Test
    void readsFormulaResultsWithTheSamePlainTextRules() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Row row = workbook.createSheet().createRow(0);
            row.createCell(0).setCellValue(202301012345d);
            Cell numericFormula = row.createCell(1);
            numericFormula.setCellFormula("A1+5");
            Cell stringFormula = row.createCell(2);
            stringFormula.setCellFormula("\" 公式文本 \"");

            ExcelCellTextReader reader = new ExcelCellTextReader(workbook);

            assertEquals("202301012350", reader.read(numericFormula));
            assertEquals("公式文本", reader.read(stringFormula));
        }
    }

    @Test
    void readsDateFormattedNumbersUsingTheirDisplayText() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Row row = workbook.createSheet().createRow(0);
            Cell date = row.createCell(0);
            date.setCellValue(LocalDate.of(2026, 8, 21));
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            date.setCellStyle(dateStyle);

            ExcelCellTextReader reader = new ExcelCellTextReader(workbook);

            assertEquals("2026-08-21", reader.read(date));
        }
    }

    @Test
    void returnsEmptyTextForMissingCellsAndColumns() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Row row = workbook.createSheet().createRow(0);
            ExcelCellTextReader reader = new ExcelCellTextReader(workbook);

            assertEquals("", reader.read((Cell) null));
            assertEquals("", reader.read((Row) null, 0));
            assertEquals("", reader.read(row, -1));
            assertEquals("", reader.read(row, 3));
        }
    }
}
