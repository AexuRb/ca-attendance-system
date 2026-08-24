package com.ca.attendance.common;

public final class ExportRowLimit {
    public static final int MAX_ROWS = 50_000;
    public static final int FETCH_LIMIT = MAX_ROWS + 1;

    private ExportRowLimit() {
    }

    public static void requireWithinLimit(int rowCount) {
        requireWithinLimit(rowCount, "导出");
    }

    public static void requireWithinLimit(int rowCount, String operation) {
        if (rowCount > MAX_ROWS) {
            throw ApiException.badRequest(operation + "结果超过 " + MAX_ROWS + " 行，请缩小筛选范围");
        }
    }
}
