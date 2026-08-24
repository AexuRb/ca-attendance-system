package com.ca.attendance.common;

public final class SqlLike {
    private SqlLike() {
    }

    public static String contains(String value) {
        String normalized = value == null ? "" : value.trim();
        return "%" + escape(normalized) + "%";
    }

    public static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
