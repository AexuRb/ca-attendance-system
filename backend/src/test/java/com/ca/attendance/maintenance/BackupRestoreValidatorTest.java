package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupRestoreValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BackupRestoreValidator validator = new BackupRestoreValidator(objectMapper);

    @Test
    void acceptsLegacyArchiveWithoutOptionalTables() throws Exception {
        Map<String, byte[]> entries = requiredEmptyEntries();

        BackupRestorePayload payload = validator.parse(entries);

        assertTrue(payload.rows().containsKey("users"));
        assertFalse(payload.rows().containsKey("app_settings"));
        assertFalse(payload.rows().containsKey("training_sessions"));
    }

    @Test
    void rejectsRowsContainingUnknownColumns() throws Exception {
        Map<String, byte[]> entries = requiredEmptyEntries();
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", 1);
        user.put("student_no", "1004231224");
        user.put("name", "测试管理员");
        user.put("password_hash", "hash");
        user.put("role", "ADMIN");
        user.put("status", "ACTIVE");
        user.put("unexpected_secret", "should-not-be-restored");
        entries.put("users.json", objectMapper.writeValueAsBytes(List.of(user)));

        ApiException error = assertThrows(ApiException.class, () -> validator.parse(entries));

        assertTrue(error.getMessage().contains("未知字段"));
        assertTrue(error.getMessage().contains("unexpected_secret"));
    }

    @Test
    void rejectsBackupsFromANewerSchemaVersion() throws Exception {
        Map<String, byte[]> entries = requiredEmptyEntries();
        entries.put("metadata.json", objectMapper.writeValueAsBytes(Map.of(
                "schemaVersion", BackupSchema.SCHEMA_VERSION + 1,
                "tables", List.of("users", "attendance_records", "operation_logs", "duty_weekday_settings")
        )));

        ApiException error = assertThrows(ApiException.class, () -> validator.parse(entries));

        assertTrue(error.getMessage().contains("版本高于当前程序"));
    }

    @Test
    void rejectsArchivesMissingARequiredTableEntry() throws Exception {
        Map<String, byte[]> entries = requiredEmptyEntries();
        entries.remove("attendance_records.json");

        ApiException error = assertThrows(ApiException.class, () -> validator.parse(entries));

        assertTrue(error.getMessage().contains("缺少 attendance_records.json"));
    }

    @Test
    void rejectsNonIntegralSchemaVersions() throws Exception {
        Map<String, byte[]> entries = requiredEmptyEntries();
        entries.put("metadata.json", objectMapper.writeValueAsBytes(Map.of(
                "schemaVersion", 4.5,
                "tables", List.of("users", "attendance_records", "operation_logs", "duty_weekday_settings")
        )));

        ApiException error = assertThrows(ApiException.class, () -> validator.parse(entries));

        assertTrue(error.getMessage().contains("版本信息不正确"));
    }

    private Map<String, byte[]> requiredEmptyEntries() throws Exception {
        List<String> requiredTables = List.of(
                "users",
                "attendance_records",
                "operation_logs",
                "duty_weekday_settings"
        );
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("metadata.json", objectMapper.writeValueAsBytes(Map.of("tables", requiredTables)));
        for (String table : requiredTables) {
            entries.put(table + ".json", objectMapper.writeValueAsBytes(List.of()));
        }
        return entries;
    }
}
