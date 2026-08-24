package com.ca.attendance.common;

public final class PaginationPolicy {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final String DEFAULT_PAGE_SIZE_TEXT = "20";
    public static final int MAX_PAGE_SIZE = 100;

    private PaginationPolicy() {
    }

    public static PageRequest normalize(int page, int pageSize) {
        int normalizedPage = Math.max(1, page);
        int normalizedPageSize = pageSize <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(pageSize, MAX_PAGE_SIZE);
        return new PageRequest(normalizedPage, normalizedPageSize);
    }

    public static int resolvePage(int requestedPage, long totalRows, int pageSize) {
        int normalizedPage = Math.max(1, requestedPage);
        if (totalRows <= 0) {
            return 1;
        }
        long lastPage = ((totalRows - 1) / pageSize) + 1;
        return (int) Math.min(normalizedPage, lastPage);
    }

    public record PageRequest(int page, int pageSize) {
    }
}
