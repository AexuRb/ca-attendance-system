package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BackupRestoreValidator {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<LinkedHashMap<String, Object>>> ROWS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    BackupRestoreValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    BackupRestorePayload parse(Map<String, byte[]> entries) {
        validateRequiredEntries(entries);
        Map<String, Object> metadata = readMetadata(entries.get("metadata.json"));
        Set<String> tableNames = validateMetadata(metadata);

        Map<String, List<LinkedHashMap<String, Object>>> rows = new LinkedHashMap<>();
        int totalRows = 0;
        for (String table : BackupSchema.RESTORE_TABLE_ORDER) {
            byte[] tableBytes = entries.get(table + ".json");
            if (tableBytes == null) {
                if (tableNames.contains(table) || !BackupSchema.OPTIONAL_RESTORE_TABLES.contains(table)) {
                    throw ApiException.badRequest("备份文件缺少 " + table + ".json");
                }
                continue;
            }
            List<LinkedHashMap<String, Object>> tableRows = readRows(tableBytes, table);
            if (tableRows.size() > BackupSchema.MAX_ROWS_PER_TABLE) {
                throw ApiException.badRequest(table + " 数据行数过多");
            }
            totalRows += tableRows.size();
            if (totalRows > BackupSchema.MAX_TOTAL_ROWS) {
                throw ApiException.badRequest("备份文件包含的数据行数过多");
            }
            validateRows(table, tableRows);
            rows.put(table, List.copyOf(tableRows));
        }
        return new BackupRestorePayload(Collections.unmodifiableMap(rows));
    }

    Set<String> supportedEntries() {
        Set<String> entries = new LinkedHashSet<>();
        entries.add("metadata.json");
        entries.add("README.txt");
        BackupSchema.RESTORE_TABLE_ORDER.forEach(table -> entries.add(table + ".json"));
        return Set.copyOf(entries);
    }

    private void validateRequiredEntries(Map<String, byte[]> entries) {
        Set<String> required = new LinkedHashSet<>();
        required.add("metadata.json");
        for (String table : BackupSchema.RESTORE_TABLE_ORDER) {
            if (!BackupSchema.OPTIONAL_RESTORE_TABLES.contains(table)) {
                required.add(table + ".json");
            }
        }
        for (String name : required) {
            if (!entries.containsKey(name)) {
                throw ApiException.badRequest("备份文件缺少 " + name);
            }
        }
    }

    private Map<String, Object> readMetadata(byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, MAP_TYPE);
        } catch (IOException ex) {
            throw ApiException.badRequest("备份元数据格式不正确");
        }
    }

    private Set<String> validateMetadata(Map<String, Object> metadata) {
        Object tables = metadata.get("tables");
        if (!(tables instanceof List<?> tableList)) {
            throw ApiException.badRequest("备份元数据缺少表信息");
        }
        Set<String> tableNames = new LinkedHashSet<>(tableList.stream().map(String::valueOf).toList());
        List<String> requiredTables = BackupSchema.RESTORE_TABLE_ORDER.stream()
                .filter(table -> !BackupSchema.OPTIONAL_RESTORE_TABLES.contains(table))
                .toList();
        if (!tableNames.containsAll(requiredTables)) {
            throw ApiException.badRequest("备份表信息不完整");
        }
        return tableNames;
    }

    private List<LinkedHashMap<String, Object>> readRows(byte[] bytes, String table) {
        try {
            return objectMapper.readValue(bytes, ROWS_TYPE);
        } catch (IOException ex) {
            throw ApiException.badRequest(table + " 数据格式不正确");
        }
    }

    private void validateRows(String table, List<LinkedHashMap<String, Object>> rows) {
        Set<String> allowedColumns = BackupSchema.TABLE_COLUMNS.get(table);
        Set<String> requiredKeys = BackupSchema.REQUIRED_KEYS.get(table);
        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> row = rows.get(index);
            if (!allowedColumns.containsAll(row.keySet())) {
                Set<String> unknownColumns = new LinkedHashSet<>(row.keySet());
                unknownColumns.removeAll(allowedColumns);
                throw ApiException.badRequest(table + " 包含未知字段：" + String.join("、", unknownColumns));
            }
            if (!row.keySet().containsAll(requiredKeys)) {
                Set<String> missingKeys = new LinkedHashSet<>(requiredKeys);
                missingKeys.removeAll(row.keySet());
                throw ApiException.badRequest(
                        table + " 第 " + (index + 1) + " 行缺少字段：" + String.join("、", missingKeys)
                );
            }
        }
    }
}
