package com.ca.attendance.common;

import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.web.multipart.MultipartFile;

public final class ExcelImportPolicy {
    public static final int MAX_DATA_ROWS = 3000;
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private ExcelImportPolicy() {
    }

    public static void validateFile(MultipartFile file, String subject) {
        String label = subject + " Excel 文件";
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择" + label);
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw ApiException.badRequest(label + "不能超过 5 MB");
        }
    }

    public static void validateRowCount(Sheet sheet, int firstDataRow, String subject) {
        long rowSpan = (long) sheet.getLastRowNum() - firstDataRow + 1;
        if (rowSpan > MAX_DATA_ROWS) {
            throw ApiException.badRequest(subject + " Excel 文件超过 " + MAX_DATA_ROWS + " 行，请拆分后导入");
        }
    }
}
