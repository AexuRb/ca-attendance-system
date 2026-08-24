package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupRestoreValidatorTest {
    @TempDir
    Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BackupRestoreValidator validator = new BackupRestoreValidator(objectMapper);
    private BackupArchiveReader reader;

    @BeforeEach
    void setUp() {
        reader = new BackupArchiveReader(tempDirectory);
    }

    @Test
    void acceptsLegacyArchiveWithoutOptionalTables() throws Exception {
        Map<String, byte[]> entries = requiredEmptyEntries();

        try (ExtractedBackupArchive archive = extract(entries)) {
            BackupRestorePayload payload = validator.parse(archive);

            assertTrue(payload.tableFiles().containsKey("users"));
            assertFalse(payload.tableFiles().containsKey("app_settings"));
            assertFalse(payload.tableFiles().containsKey("training_sessions"));
        }
    }

    @Test
    void rejectsCurrentSchemaArchiveMissingADeclaredTable() throws Exception {
        Map<String, byte[]> entries = currentEmptyEntries();
        entries.remove("training_sessions.json");

        ApiException error = assertInvalid(entries);

        assertTrue(error.getMessage().contains("training_sessions.json"));
    }

    @Test
    void acceptsSchemaThreeArchiveWithoutRepairSequenceTable() throws Exception {
        Map<String, byte[]> entries = currentEmptyEntries();
        entries.remove("repair_case_sequences.json");
        List<String> schemaThreeTables = BackupSchema.RESTORE_TABLE_ORDER.stream()
                .filter(table -> !"repair_case_sequences".equals(table))
                .toList();
        entries.put("metadata.json", objectMapper.writeValueAsBytes(Map.of(
                "schemaVersion", 3,
                "tables", schemaThreeTables
        )));

        try (ExtractedBackupArchive archive = extract(entries)) {
            BackupRestorePayload payload = validator.parse(archive);

            assertFalse(payload.tableFiles().containsKey("repair_case_sequences"));
            assertTrue(payload.tableFiles().containsKey("repair_cases"));
        }
    }

    @Test
    void rejectsTableEntryNotDeclaredByMetadata() throws Exception {
        Map<String, byte[]> entries = requiredEmptyEntries();
        entries.put("training_sessions.json", objectMapper.writeValueAsBytes(List.of()));

        ApiException error = assertInvalid(entries);

        assertTrue(error.getMessage().contains("元数据与实际条目不一致"));
    }

    @Test
    void rejectsDuplicateAndUnknownMetadataTables() throws Exception {
        Map<String, byte[]> entries = requiredEmptyEntries();
        entries.put("metadata.json", objectMapper.writeValueAsBytes(Map.of(
                "tables", List.of("users", "users", "attendance_records", "operation_logs", "unknown_table")
        )));

        ApiException error = assertInvalid(entries);

        assertTrue(error.getMessage().contains("重复") || error.getMessage().contains("不支持"));
    }

    @Test
    void rejectsRowsContainingUnknownColumns() throws Exception {
        Map<String, byte[]> entries = requiredEmptyEntries();
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", 1);
        user.put("student_no", "9900000001");
        user.put("name", "测试管理员");
        user.put("password_hash", "hash");
        user.put("role", "ADMIN");
        user.put("status", "ACTIVE");
        user.put("unexpected_secret", "should-not-be-restored");
        entries.put("users.json", objectMapper.writeValueAsBytes(List.of(user)));

        ApiException error = assertInvalid(entries);

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

        ApiException error = assertInvalid(entries);

        assertTrue(error.getMessage().contains("版本高于当前程序"));
    }

    @Test
    void rejectsArchivesMissingARequiredTableEntry() throws Exception {
        Map<String, byte[]> entries = requiredEmptyEntries();
        entries.remove("attendance_records.json");

        ApiException error = assertInvalid(entries);

        assertTrue(error.getMessage().contains("缺少 attendance_records.json"));
    }

    @Test
    void rejectsNonIntegralSchemaVersions() throws Exception {
        Map<String, byte[]> entries = requiredEmptyEntries();
        entries.put("metadata.json", objectMapper.writeValueAsBytes(Map.of(
                "schemaVersion", 4.5,
                "tables", List.of("users", "attendance_records", "operation_logs", "duty_weekday_settings")
        )));

        ApiException error = assertInvalid(entries);

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

    private Map<String, byte[]> currentEmptyEntries() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("metadata.json", objectMapper.writeValueAsBytes(Map.of(
                "schemaVersion", BackupSchema.SCHEMA_VERSION,
                "tables", BackupSchema.RESTORE_TABLE_ORDER
        )));
        for (String table : BackupSchema.RESTORE_TABLE_ORDER) {
            entries.put(table + ".json", objectMapper.writeValueAsBytes(List.of()));
        }
        return entries;
    }

    private ApiException assertInvalid(Map<String, byte[]> entries) {
        return assertThrows(ApiException.class, () -> {
            try (ExtractedBackupArchive archive = extract(entries)) {
                validator.parse(archive);
            }
        });
    }

    private ExtractedBackupArchive extract(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return reader.extract(
                new MockMultipartFile("file", "backup_test.zip", "application/zip", output.toByteArray()),
                validator.supportedEntries()
        );
    }
}
