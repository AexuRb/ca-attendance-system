package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BackupRestoreValidator {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<LinkedHashMap<String, Object>> ROW_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    BackupRestoreValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    BackupRestorePayload parse(ExtractedBackupArchive archive) {
        validateRequiredEntries(archive.names());
        Map<String, Object> metadata = readMetadata(archive.entry("metadata.json"));
        Set<String> tableNames = validateMetadata(metadata);

        Map<String, Path> tableFiles = new LinkedHashMap<>();
        int totalRows = 0;
        for (String table : BackupSchema.RESTORE_TABLE_ORDER) {
            Path tableFile = archive.entry(table + ".json");
            if (tableFile == null) {
                if (tableNames.contains(table) || !BackupSchema.OPTIONAL_RESTORE_TABLES.contains(table)) {
                    throw ApiException.badRequest("备份文件缺少 " + table + ".json");
                }
                continue;
            }
            int tableRows = validateTable(tableFile, table);
            totalRows += tableRows;
            if (totalRows > BackupArchiveLimits.MAX_TOTAL_ROWS) {
                throw ApiException.badRequest("备份文件包含的数据行数过多");
            }
            tableFiles.put(table, tableFile);
        }
        return new BackupRestorePayload(Collections.unmodifiableMap(new LinkedHashMap<>(tableFiles)));
    }

    Set<String> supportedEntries() {
        Set<String> entries = new LinkedHashSet<>();
        entries.add("metadata.json");
        entries.add("README.txt");
        BackupSchema.RESTORE_TABLE_ORDER.forEach(table -> entries.add(table + ".json"));
        return Set.copyOf(entries);
    }

    private void validateRequiredEntries(Set<String> entries) {
        Set<String> required = new LinkedHashSet<>();
        required.add("metadata.json");
        for (String table : BackupSchema.RESTORE_TABLE_ORDER) {
            if (!BackupSchema.OPTIONAL_RESTORE_TABLES.contains(table)) {
                required.add(table + ".json");
            }
        }
        for (String name : required) {
            if (!entries.contains(name)) {
                throw ApiException.badRequest("备份文件缺少 " + name);
            }
        }
    }

    private Map<String, Object> readMetadata(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), MAP_TYPE);
        } catch (IOException ex) {
            throw ApiException.badRequest("备份元数据格式不正确");
        }
    }

    private Set<String> validateMetadata(Map<String, Object> metadata) {
        validateSchemaVersion(metadata.get("schemaVersion"));
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

    private void validateSchemaVersion(Object value) {
        if (value == null) {
            return;
        }
        int schemaVersion;
        try {
            schemaVersion = new BigDecimal(String.valueOf(value)).intValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw ApiException.badRequest("备份版本信息不正确");
        }
        if (schemaVersion < 1) {
            throw ApiException.badRequest("备份版本信息不正确");
        }
        if (schemaVersion > BackupSchema.SCHEMA_VERSION) {
            throw ApiException.badRequest("备份版本高于当前程序，请先升级程序后再恢复");
        }
    }

    private int validateTable(Path path, String table) {
        try (JsonParser parser = objectMapper.getFactory().createParser(path.toFile())) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw ApiException.badRequest(table + " 数据格式不正确");
            }
            int rowCount = 0;
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
                if (token != JsonToken.START_OBJECT) {
                    throw ApiException.badRequest(table + " 数据格式不正确");
                }
                rowCount++;
                if (rowCount > BackupArchiveLimits.MAX_ROWS_PER_TABLE) {
                    throw ApiException.badRequest(table + " 数据行数过多");
                }
                validateRow(table, rowCount, objectMapper.readValue(parser, ROW_TYPE));
            }
            if (parser.nextToken() != null) {
                throw ApiException.badRequest(table + " 数据格式不正确");
            }
            return rowCount;
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw ApiException.badRequest(table + " 数据格式不正确");
        }
    }

    private void validateRow(String table, int rowNumber, Map<String, Object> row) {
        Set<String> allowedColumns = BackupSchema.TABLE_COLUMNS.get(table);
        Set<String> requiredKeys = BackupSchema.REQUIRED_KEYS.get(table);
        if (!allowedColumns.containsAll(row.keySet())) {
            Set<String> unknownColumns = new LinkedHashSet<>(row.keySet());
            unknownColumns.removeAll(allowedColumns);
            throw ApiException.badRequest(table + " 包含未知字段：" + String.join("、", unknownColumns));
        }
        if (!row.keySet().containsAll(requiredKeys)) {
            Set<String> missingKeys = new LinkedHashSet<>(requiredKeys);
            missingKeys.removeAll(row.keySet());
            throw ApiException.badRequest(
                    table + " 第 " + rowNumber + " 行缺少字段：" + String.join("、", missingKeys)
            );
        }
    }
}
