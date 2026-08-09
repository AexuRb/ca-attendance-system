package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.JdbcTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DatabaseRestoreExecutor {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    DatabaseRestoreExecutor(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    Map<String, Integer> restore(BackupRestorePayload payload) {
        Map<String, Integer> restoredRows = new LinkedHashMap<>();
        jdbc.execute("PRAGMA defer_foreign_keys = ON");
        for (String table : BackupSchema.CLEAR_TABLE_ORDER) {
            if (shouldClear(payload, table)) {
                jdbc.update("DELETE FROM " + table);
            }
        }
        for (String table : BackupSchema.RESTORE_TABLE_ORDER) {
            if (shouldRestore(payload, table)) {
                restoredRows.put(table, restoreTable(table, payload.rows().get(table)));
            }
        }
        synchronizeRepairCaseSequences();
        return restoredRows;
    }

    private void synchronizeRepairCaseSequences() {
        jdbc.update("""
                INSERT INTO repair_case_sequences (sequence_date, last_value, updated_at)
                SELECT
                  substr(case_no, 5, 8),
                  MAX(CAST(substr(case_no, 14) AS INTEGER)),
                  datetime('now', 'localtime')
                FROM repair_cases
                WHERE case_no GLOB 'JXWX[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9]*'
                GROUP BY substr(case_no, 5, 8)
                ON CONFLICT(sequence_date) DO UPDATE SET
                  last_value = MAX(repair_case_sequences.last_value, excluded.last_value),
                  updated_at = datetime('now', 'localtime')
                """);
    }

    private int restoreTable(String table, List<? extends Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map<String, Object> row : rows) {
            List<String> columns = new ArrayList<>(row.keySet());
            String columnSql = columns.stream().map(this::quote).reduce((left, right) -> left + ", " + right).orElse("");
            String placeholders = columns.stream().map(column -> "?").reduce((left, right) -> left + ", " + right).orElse("");
            Object[] values = columns.stream()
                    .map(column -> restoreValue(table, column, row.get(column)))
                    .toArray();
            jdbc.update("INSERT INTO " + table + " (" + columnSql + ") VALUES (" + placeholders + ")", values);
            count++;
        }
        return count;
    }

    private boolean shouldRestore(BackupRestorePayload payload, String table) {
        return payload.rows().containsKey(table) || !BackupSchema.OPTIONAL_RESTORE_TABLES.contains(table);
    }

    private boolean shouldClear(BackupRestorePayload payload, String table) {
        return !"app_settings".equals(table) || payload.rows().containsKey(table);
    }

    private Object restoreValue(String table, String column, Object value) {
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

    private String quote(String column) {
        return "`" + column + "`";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"json_encode_failed\"}";
        }
    }
}
