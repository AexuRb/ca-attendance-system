package com.ca.attendance.common;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;

import java.math.BigDecimal;
import java.util.Objects;

public final class ExcelCellTextReader {
    private final DataFormatter formatter = new DataFormatter();
    private final FormulaEvaluator evaluator;

    public ExcelCellTextReader(Workbook workbook) {
        this.evaluator = Objects.requireNonNull(workbook, "workbook")
                .getCreationHelper()
                .createFormulaEvaluator();
    }

    public String read(Row row, int columnIndex) {
        if (row == null || columnIndex < 0) {
            return "";
        }
        return read(row.getCell(columnIndex));
    }

    public String read(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case BLANK -> "";
            case STRING -> trim(cell.getStringCellValue());
            case NUMERIC -> numericText(cell.getNumericCellValue(), cell);
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case FORMULA -> formulaText(cell);
            case ERROR -> trim(formatter.formatCellValue(cell, evaluator));
            default -> "";
        };
    }

    private String formulaText(Cell cell) {
        CellValue value = evaluator.evaluate(cell);
        if (value == null) {
            return "";
        }
        return switch (value.getCellType()) {
            case BLANK -> "";
            case STRING -> trim(value.getStringValue());
            case NUMERIC -> numericText(value.getNumberValue(), cell);
            case BOOLEAN -> Boolean.toString(value.getBooleanValue());
            case ERROR -> trim(formatter.formatCellValue(cell, evaluator));
            default -> "";
        };
    }

    private String numericText(double value, Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            return trim(formatter.formatCellValue(cell, evaluator));
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String trim(String text) {
        return text == null ? "" : text.trim();
    }
}
