package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

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
    private final BackupRestoreValueConverter valueConverter;

    BackupRestoreValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.valueConverter = new BackupRestoreValueConverter(objectMapper);
    }

    BackupRestorePayload parse(ExtractedBackupArchive archive) {
        validateRequiredEntries(archive.names());
        Map<String, Object> metadata = readMetadata(archive.entry("metadata.json"));
        int schemaVersion = validateSchemaVersion(metadata.get("schemaVersion"));
        Set<String> tableNames = validateMetadata(metadata, schemaVersion);
        validateDeclaredEntries(archive.names(), tableNames);

        Map<String, Path> tableFiles = new LinkedHashMap<>();
        for (String table : BackupSchema.RESTORE_TABLE_ORDER) {
            Path tableFile = archive.entry(table + ".json");
            if (tableFile == null) {
                continue;
            }
            validateTable(tableFile, table);
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
        if (!entries.contains("metadata.json")) {
            throw ApiException.badRequest("备份文件缺少 metadata.json");
        }
    }

    private Map<String, Object> readMetadata(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), MAP_TYPE);
        } catch (JacksonException ex) {
            throw ApiException.badRequest("备份元数据格式不正确");
        }
    }

    private Set<String> validateMetadata(Map<String, Object> metadata, int schemaVersion) {
        Object tables = metadata.get("tables");
        if (!(tables instanceof List<?> tableList)) {
            throw ApiException.badRequest("备份元数据缺少表信息");
        }
        Set<String> tableNames = new LinkedHashSet<>();
        for (Object value : tableList) {
            if (!(value instanceof String table) || table.isBlank()) {
                throw ApiException.badRequest("备份表信息不正确");
            }
            if (!BackupSchema.TABLE_COLUMNS.containsKey(table)) {
                throw ApiException.badRequest("备份表信息包含不支持的表：" + table);
            }
            if (!tableNames.add(table)) {
                throw ApiException.badRequest("备份表信息包含重复表：" + table);
            }
        }
        Set<String> missingRequired = new LinkedHashSet<>(BackupSchema.requiredTables(schemaVersion));
        missingRequired.removeAll(tableNames);
        if (!missingRequired.isEmpty()) {
            throw ApiException.badRequest("备份表信息不完整：" + String.join("、", missingRequired));
        }
        return tableNames;
    }

    private void validateDeclaredEntries(Set<String> entries, Set<String> tableNames) {
        Set<String> actualTables = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry.endsWith(".json") && !"metadata.json".equals(entry)) {
                actualTables.add(entry.substring(0, entry.length() - 5));
            }
        }
        Set<String> missingEntries = new LinkedHashSet<>(tableNames);
        missingEntries.removeAll(actualTables);
        if (!missingEntries.isEmpty()) {
            String table = missingEntries.iterator().next();
            throw ApiException.badRequest("备份元数据与实际条目不一致：缺少 " + table + ".json");
        }
        Set<String> undeclaredEntries = new LinkedHashSet<>(actualTables);
        undeclaredEntries.removeAll(tableNames);
        if (!undeclaredEntries.isEmpty()) {
            String table = undeclaredEntries.iterator().next();
            throw ApiException.badRequest("备份元数据与实际条目不一致：未声明 " + table + ".json");
        }
    }

    private int validateSchemaVersion(Object value) {
        if (value == null) {
            return BackupSchema.LEGACY_SCHEMA_VERSION;
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
        return schemaVersion;
    }

    private void validateTable(Path path, String table) {
        try (JsonParser parser = objectMapper.createParser(path)) {
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
                validateRow(table, rowCount, parser.readValueAs(ROW_TYPE));
            }
            if (parser.nextToken() != null) {
                throw ApiException.badRequest(table + " 数据格式不正确");
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (JacksonException ex) {
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
        row.forEach((column, value) -> valueConverter.convert(table, rowNumber, column, value));
    }
}
