package com.ca.attendance.common;

public final class JdbcWriteChecks {
    private JdbcWriteChecks() {
    }

    public static void requireOne(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw ApiException.conflict(message);
        }
    }
}
