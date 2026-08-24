package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DatabaseRestoreExecutor {
    private static final TypeReference<LinkedHashMap<String, Object>> ROW_TYPE = new TypeReference<>() {
    };
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final BackupRestoreValueConverter valueConverter;

    DatabaseRestoreExecutor(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.valueConverter = new BackupRestoreValueConverter(objectMapper);
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
                restoredRows.put(table, restoreTable(table, payload.tableFiles().get(table)));
            }
        }
        if (payload.tableFiles().containsKey("repair_cases")
                || payload.tableFiles().containsKey("repair_case_sequences")) {
            synchronizeRepairCaseSequences();
        }
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

    private int restoreTable(String table, java.nio.file.Path tableFile) {
        if (tableFile == null) {
            return 0;
        }
        try (JsonParser parser = objectMapper.createParser(tableFile)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw ApiException.badRequest(table + " 数据格式不正确");
            }
            int count = 0;
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
                Map<String, Object> row = parser.readValueAs(ROW_TYPE);
                count++;
                insertRow(table, count, row);
            }
            return count;
        } catch (ApiException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw ApiException.badRequest(table + " 数据格式不正确");
        }
    }

    private void insertRow(String table, int rowNumber, Map<String, Object> row) {
        List<String> columns = new ArrayList<>(row.keySet());
        String columnSql = columns.stream().map(this::quote).reduce((left, right) -> left + ", " + right).orElse("");
        String placeholders = columns.stream().map(column -> "?").reduce((left, right) -> left + ", " + right).orElse("");
        Object[] values = columns.stream()
                .map(column -> valueConverter.convert(table, rowNumber, column, row.get(column)))
                .toArray();
        jdbc.update("INSERT INTO " + table + " (" + columnSql + ") VALUES (" + placeholders + ")", values);
    }

    private boolean shouldRestore(BackupRestorePayload payload, String table) {
        return payload.tableFiles().containsKey(table) || !BackupSchema.OPTIONAL_RESTORE_TABLES.contains(table);
    }

    private boolean shouldClear(BackupRestorePayload payload, String table) {
        return payload.tableFiles().containsKey(table);
    }

    private String quote(String column) {
        return "`" + column + "`";
    }

}
