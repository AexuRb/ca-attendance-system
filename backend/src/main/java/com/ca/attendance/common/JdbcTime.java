package com.ca.attendance.common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

public final class JdbcTime {
    private JdbcTime() {
    }

    public static LocalDate localDate(ResultSet result, String column) throws SQLException {
        Object raw = result.getObject(column);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return fromEpoch(number.longValue()).toLocalDate();
        }
        String text = raw.toString().trim();
        if (text.length() < 10) {
            throw new SQLException("Invalid date value in " + column + ": " + text);
        }
        return LocalDate.parse(text.substring(0, 10));
    }

    public static LocalDateTime localDateTime(ResultSet result, String column) throws SQLException {
        Object raw = result.getObject(column);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return fromEpoch(number.longValue());
        }
        String text = raw.toString().trim().replace('T', ' ');
        try {
            return java.sql.Timestamp.valueOf(text).toLocalDateTime();
        } catch (IllegalArgumentException ex) {
            throw new SQLException("Invalid date-time value in " + column + ": " + text, ex);
        }
    }

    public static LocalTime localTime(ResultSet result, String column) throws SQLException {
        Object raw = result.getObject(column);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return fromEpoch(number.longValue()).toLocalTime();
        }
        String text = raw.toString().trim();
        int separator = Math.max(text.lastIndexOf(' '), text.lastIndexOf('T'));
        if (separator >= 0) {
            text = text.substring(separator + 1);
        }
        try {
            return LocalTime.parse(text);
        } catch (IllegalArgumentException ex) {
            throw new SQLException("Invalid time value in " + column + ": " + text, ex);
        }
    }

    private static LocalDateTime fromEpoch(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
