package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.JdbcTime;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

final class BackupRestoreValueConverter {
    private final ObjectMapper objectMapper;

    BackupRestoreValueConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Object convert(String table, int rowNumber, String column, Object value) {
        try {
            return convertValue(table, column, value);
        } catch (RuntimeException ex) {
            throw ApiException.badRequest(
                    table + " 第 " + rowNumber + " 行字段 " + column + " 格式不正确"
            );
        }
    }

    private Object convertValue(String table, String column, Object value) {
        if (value == null) {
            return null;
        }
        if (BackupSchema.DATE_COLUMNS.getOrDefault(table, Set.of()).contains(column)) {
            return toSqlDate(value);
        }
        if (BackupSchema.TIME_COLUMNS.getOrDefault(table, Set.of()).contains(column)) {
            return toSqlTime(value);
        }
        if (BackupSchema.DATE_TIME_COLUMNS.getOrDefault(table, Set.of()).contains(column)) {
            return toTimestamp(value);
        }
        if (BackupSchema.JSON_COLUMNS.contains(column) && !(value instanceof String)) {
            return toJson(value);
        }
        return value;
    }

    private String toSqlDate(Object value) {
        LocalDate date;
        if (value instanceof Number number) {
            date = new java.sql.Date(number.longValue()).toLocalDate();
        } else if (value instanceof List<?> parts && parts.size() >= 3) {
            date = LocalDate.of(intPart(parts, 0), intPart(parts, 1), intPart(parts, 2));
        } else if (value instanceof String text && text.trim().length() >= 10) {
            date = LocalDate.parse(text.trim().substring(0, 10));
        } else {
            throw ApiException.badRequest("备份日期格式不正确");
        }
        return JdbcTime.databaseDate(date);
    }

    private String toSqlTime(Object value) {
        LocalTime time;
        if (value instanceof Number number) {
            time = new java.sql.Time(number.longValue()).toLocalTime();
        } else if (value instanceof List<?> parts && parts.size() >= 2) {
            time = LocalTime.of(intPart(parts, 0), intPart(parts, 1), parts.size() > 2 ? intPart(parts, 2) : 0);
        } else if (value instanceof String text) {
            String normalized = text.trim();
            int separator = Math.max(normalized.lastIndexOf(' '), normalized.lastIndexOf('T'));
            if (separator >= 0) {
                normalized = normalized.substring(separator + 1);
            }
            if (normalized.length() == 5) {
                normalized += ":00";
            }
            time = LocalTime.parse(normalized);
        } else {
            throw ApiException.badRequest("备份时刻格式不正确");
        }
        return JdbcTime.databaseTime(time);
    }

    private Timestamp toTimestamp(Object value) {
        if (value instanceof Number number) {
            return new Timestamp(number.longValue());
        }
        if (value instanceof List<?> parts && parts.size() >= 3) {
            LocalDate date = LocalDate.of(intPart(parts, 0), intPart(parts, 1), intPart(parts, 2));
            LocalTime time = LocalTime.of(
                    parts.size() > 3 ? intPart(parts, 3) : 0,
                    parts.size() > 4 ? intPart(parts, 4) : 0,
                    parts.size() > 5 ? intPart(parts, 5) : 0,
                    parts.size() > 6 ? intPart(parts, 6) : 0
            );
            return Timestamp.valueOf(LocalDateTime.of(date, time));
        }
        if (value instanceof String text) {
            return Timestamp.valueOf(text.trim().replace('T', ' '));
        }
        throw ApiException.badRequest("备份时间格式不正确");
    }

    private int intPart(List<?> parts, int index) {
        Object value = parts.get(index);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw ApiException.badRequest("备份 JSON 字段格式不正确");
        }
    }
}
