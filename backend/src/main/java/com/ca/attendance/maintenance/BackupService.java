package com.ca.attendance.maintenance;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class BackupService {
    private static final DateTimeFormatter FILENAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final long MAX_RESTORE_BYTES = 50L * 1024 * 1024;
    private static final List<TableExport> TABLES = List.of(
            new TableExport("users", "SELECT * FROM users ORDER BY id"),
            new TableExport("attendance_records", "SELECT * FROM attendance_records ORDER BY duty_date DESC, check_in_time DESC, id DESC"),
            new TableExport("operation_logs", "SELECT * FROM operation_logs ORDER BY created_at DESC, id DESC"),
            new TableExport("duty_weekday_settings", "SELECT * FROM duty_weekday_settings ORDER BY weekday"),
            new TableExport("app_settings", "SELECT * FROM app_settings ORDER BY setting_key")
    );
    private static final List<String> RESTORE_TABLE_ORDER = List.of(
            "users",
            "duty_weekday_settings",
            "app_settings",
            "attendance_records",
            "operation_logs"
    );
    private static final List<String> CLEAR_TABLE_ORDER = List.of(
            "operation_logs",
            "attendance_records",
            "app_settings",
            "duty_weekday_settings",
            "users"
    );
    private static final Set<String> OPTIONAL_RESTORE_TABLES = Set.of("app_settings");
    private static final Map<String, Set<String>> TABLE_COLUMNS = Map.of(
            "users", Set.of(
                    "id", "student_no", "name", "password_hash", "role", "status", "phone", "major", "grade", "qq",
                    "must_change_password", "last_login_at", "disabled_at", "disabled_by", "created_by", "updated_by",
                    "created_at", "updated_at"
            ),
            "attendance_records", Set.of(
                    "id", "user_id", "student_no_snapshot", "name_snapshot", "duty_date", "duty_weekday", "is_duty_day",
                    "check_in_time", "check_out_time", "check_in_status", "check_out_status", "check_in_reviewed_by",
                    "check_out_reviewed_by", "check_in_reviewed_at", "check_out_reviewed_at", "check_in_reject_reason",
                    "check_out_reject_reason", "duration_minutes", "valid_hours", "effective_status", "source",
                    "manual_reason", "created_by", "updated_by", "created_at", "updated_at"
            ),
            "operation_logs", Set.of(
                    "id", "operator_user_id", "operator_student_no", "operator_name", "action_type", "target_type",
                    "target_id", "before_data", "after_data", "reason", "ip_address", "user_agent", "created_at"
            ),
            "duty_weekday_settings", Set.of(
                    "weekday", "weekday_name", "enabled", "updated_by", "created_at", "updated_at"
            ),
            "app_settings", Set.of(
                    "setting_key", "setting_value", "description", "updated_by", "created_at", "updated_at"
            )
    );
    private static final Map<String, Set<String>> REQUIRED_KEYS = Map.of(
            "users", Set.of("id", "student_no", "name", "password_hash", "role", "status"),
            "attendance_records", Set.of("id", "user_id", "student_no_snapshot", "name_snapshot", "duty_date", "check_in_time"),
            "operation_logs", Set.of("id", "action_type", "target_type", "created_at"),
            "duty_weekday_settings", Set.of("weekday", "weekday_name", "enabled"),
            "app_settings", Set.of("setting_key", "setting_value")
    );
    private static final Map<String, Set<String>> DATE_COLUMNS = Map.of(
            "attendance_records", Set.of("duty_date")
    );
    private static final Map<String, Set<String>> DATE_TIME_COLUMNS = Map.of(
            "users", Set.of("last_login_at", "disabled_at", "created_at", "updated_at"),
            "attendance_records", Set.of(
                    "check_in_time", "check_out_time", "check_in_reviewed_at", "check_out_reviewed_at",
                    "created_at", "updated_at"
            ),
            "operation_logs", Set.of("created_at"),
            "duty_weekday_settings", Set.of("created_at", "updated_at"),
            "app_settings", Set.of("created_at", "updated_at")
    );
    private static final Set<String> JSON_COLUMNS = Set.of("before_data", "after_data");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<LinkedHashMap<String, Object>>> ROWS_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final TokenService tokenService;

    public BackupService(JdbcTemplate jdbc, ObjectMapper objectMapper, TransactionTemplate transactionTemplate, TokenService tokenService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.tokenService = tokenService;
    }

    public BackupItem create() {
        AuthUser current = AuthContext.current();
        if (current.role() != Role.PRESIDENT && current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有会长或管理员可以备份数据");
        }

        try {
            Path dir = backupDir();
            Files.createDirectories(dir);
            Path target = newBackupPath(dir);
            String filename = target.getFileName().toString();

            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
                writeJson(zip, "metadata.json", metadata(current));
                for (TableExport table : TABLES) {
                    writeJson(zip, table.name() + ".json", jdbc.queryForList(table.sql()));
                }
                writeText(zip, "README.txt", """
                        计算机协会签到签退系统数据备份

                        本备份由系统后台生成，包含 users、attendance_records、operation_logs、duty_weekday_settings、app_settings 五张核心表。
                        文件格式为 JSON，适合留档、核对和必要时人工恢复。
                        请勿把包含真实成员信息的备份文件上传到公开仓库。
                        """);
            }
            return toItem(target);
        } catch (IOException ex) {
            throw ApiException.badRequest("生成备份失败");
        }
    }

    public List<BackupItem> list() {
        AuthUser current = AuthContext.current();
        if (current.role() != Role.PRESIDENT && current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有会长或管理员可以查看备份");
        }
        try {
            Path dir = backupDir();
            if (!Files.isDirectory(dir)) {
                return List.of();
            }
            try (var stream = Files.list(dir)) {
                return stream
                        .filter(path -> path.getFileName().toString().endsWith(".zip"))
                        .sorted(Comparator.comparing(this::lastModified).reversed())
                        .map(this::toItem)
                        .toList();
            }
        } catch (IOException ex) {
            throw ApiException.badRequest("读取备份列表失败");
        }
    }

    public BackupFile download(String filename) {
        AuthUser current = AuthContext.current();
        if (current.role() != Role.PRESIDENT && current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有会长或管理员可以下载备份");
        }
        Path file = resolveBackup(filename);
        try {
            return new BackupFile(file.getFileName().toString(), Files.readAllBytes(file));
        } catch (IOException ex) {
            throw ApiException.notFound("备份文件不存在");
        }
    }

    public void delete(String filename) {
        if (AuthContext.current().role() != Role.ADMIN) {
            throw ApiException.forbidden("只有管理员可以删除备份");
        }
        Path file = resolveBackup(filename);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw ApiException.badRequest("删除备份失败");
        }
    }

    public RestoreResult restore(MultipartFile file) {
        AuthUser current = AuthContext.current();
        if (current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有管理员可以恢复备份");
        }
        BackupPayload payload = readRestorePayload(file);
        BackupItem safetyBackup = create();

        RestoreResult result = transactionTemplate.execute(status -> {
            try {
                return restorePayload(payload, safetyBackup, current);
            } catch (RuntimeException ex) {
                status.setRollbackOnly();
                throw ex;
            }
        });
        tokenService.revokeAll();
        return result;
    }

    private RestoreResult restorePayload(BackupPayload payload, BackupItem safetyBackup, AuthUser current) {
        Map<String, Integer> restoredRows = new LinkedHashMap<>();
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            for (String table : CLEAR_TABLE_ORDER) {
                if (shouldRestoreTable(payload, table)) {
                    jdbc.update("DELETE FROM " + table);
                }
            }
            for (String table : RESTORE_TABLE_ORDER) {
                if (shouldRestoreTable(payload, table)) {
                    int count = restoreTable(table, payload.rows().get(table));
                    restoredRows.put(table, count);
                }
            }
        } finally {
            jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        }

        RestoreResult result = new RestoreResult(safetyBackup, restoredRows, restoredRows.values().stream().mapToInt(Integer::intValue).sum());
        logRestore(current, result);
        return result;
    }

    private BackupPayload readRestorePayload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择备份 zip 文件");
        }
        if (file.getSize() > MAX_RESTORE_BYTES) {
            throw ApiException.badRequest("备份文件过大，请确认是否为系统生成的备份");
        }
        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!originalFilename.endsWith(".zip")) {
            throw ApiException.badRequest("只能上传系统生成的 zip 备份文件");
        }

        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.contains("/") || name.contains("\\")) {
                    throw ApiException.badRequest("备份文件结构不正确");
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                zip.transferTo(out);
                entries.put(name, out.toByteArray());
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw ApiException.badRequest("备份文件读取失败");
        }

        validateRequiredEntries(entries);
        Map<String, Object> metadata = readMetadata(entries.get("metadata.json"));
        Set<String> tableNames = validateMetadata(metadata);

        Map<String, List<LinkedHashMap<String, Object>>> rows = new LinkedHashMap<>();
        for (String table : RESTORE_TABLE_ORDER) {
            byte[] tableBytes = entries.get(table + ".json");
            if (tableBytes == null) {
                if (tableNames.contains(table) || !OPTIONAL_RESTORE_TABLES.contains(table)) {
                    throw ApiException.badRequest("备份文件缺少 " + table + ".json");
                }
                continue;
            }
            List<LinkedHashMap<String, Object>> tableRows = readRows(tableBytes, table);
            validateRows(table, tableRows);
            rows.put(table, tableRows);
        }
        return new BackupPayload(metadata, rows);
    }

    private void validateRequiredEntries(Map<String, byte[]> entries) {
        Set<String> required = new LinkedHashSet<>();
        required.add("metadata.json");
        for (String table : RESTORE_TABLE_ORDER) {
            if (OPTIONAL_RESTORE_TABLES.contains(table)) {
                continue;
            }
            required.add(table + ".json");
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
        List<String> requiredTables = RESTORE_TABLE_ORDER.stream()
                .filter(table -> !OPTIONAL_RESTORE_TABLES.contains(table))
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
        Set<String> allowedColumns = TABLE_COLUMNS.get(table);
        Set<String> requiredKeys = REQUIRED_KEYS.get(table);
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            if (!allowedColumns.containsAll(row.keySet())) {
                Set<String> unknownColumns = new LinkedHashSet<>(row.keySet());
                unknownColumns.removeAll(allowedColumns);
                throw ApiException.badRequest(table + " 包含未知字段：" + String.join("、", unknownColumns));
            }
            if (!row.keySet().containsAll(requiredKeys)) {
                Set<String> missingKeys = new LinkedHashSet<>(requiredKeys);
                missingKeys.removeAll(row.keySet());
                throw ApiException.badRequest(table + " 第 " + (i + 1) + " 行缺少字段：" + String.join("、", missingKeys));
            }
        }
    }

    private int restoreTable(String table, List<LinkedHashMap<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map<String, Object> row : rows) {
            List<String> columns = new ArrayList<>(row.keySet());
            String columnSql = columns.stream().map(this::quote).reduce((a, b) -> a + ", " + b).orElse("");
            String placeholderSql = columns.stream().map(column -> "?").reduce((a, b) -> a + ", " + b).orElse("");
            Object[] values = columns.stream()
                    .map(column -> restoreValue(table, column, row.get(column)))
                    .toArray();
            jdbc.update("INSERT INTO " + table + " (" + columnSql + ") VALUES (" + placeholderSql + ")", values);
            count++;
        }
        return count;
    }

    private boolean shouldRestoreTable(BackupPayload payload, String table) {
        return payload.rows().containsKey(table) || !OPTIONAL_RESTORE_TABLES.contains(table);
    }

    private Object restoreValue(String table, String column, Object value) {
        if (value == null) {
            return null;
        }
        if (DATE_COLUMNS.getOrDefault(table, Set.of()).contains(column)) {
            return toSqlDate(value);
        }
        if (DATE_TIME_COLUMNS.getOrDefault(table, Set.of()).contains(column)) {
            return toTimestamp(value);
        }
        if (JSON_COLUMNS.contains(column) && !(value instanceof String)) {
            return toJson(value);
        }
        return value;
    }

    private java.sql.Date toSqlDate(Object value) {
        if (value instanceof Number number) {
            return new java.sql.Date(number.longValue());
        }
        if (value instanceof List<?> parts && parts.size() >= 3) {
            return java.sql.Date.valueOf(LocalDate.of(intPart(parts, 0), intPart(parts, 1), intPart(parts, 2)));
        }
        if (value instanceof String text) {
            return java.sql.Date.valueOf(text.trim());
        }
        throw ApiException.badRequest("备份日期格式不正确");
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

    private void logRestore(AuthUser current, RestoreResult result) {
        Long operatorId = userIdExists(current.id()) ? current.id() : null;
        jdbc.update("""
                INSERT INTO operation_logs (
                  operator_user_id, operator_student_no, operator_name, action_type, target_type,
                  target_id, before_data, after_data, reason
                )
                VALUES (?, ?, ?, 'RESTORE_BACKUP', 'maintenance_backups', NULL, NULL, ?, ?)
                """,
                operatorId,
                current.studentNo(),
                current.name(),
                toJson(result),
                "管理员恢复备份，恢复前自动备份：" + result.safetyBackup().filename()
        );
    }

    private boolean userIdExists(long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private String quote(String column) {
        return "`" + column + "`";
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"json_encode_failed\"}";
        }
    }

    private Map<String, Object> metadata(AuthUser current) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("system", "计算机协会签到签退系统");
        metadata.put("createdAt", LocalDateTime.now());
        metadata.put("operatorStudentNo", current.studentNo());
        metadata.put("operatorName", current.name());
        metadata.put("tables", TABLES.stream().map(TableExport::name).toList());
        return metadata;
    }

    private void writeJson(ZipOutputStream zip, String name, Object value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(out, value);
        writeBytes(zip, name, out.toByteArray());
    }

    private void writeText(ZipOutputStream zip, String name, String value) throws IOException {
        writeBytes(zip, name, value.getBytes(StandardCharsets.UTF_8));
    }

    private void writeBytes(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private Path resolveBackup(String filename) {
        if (filename == null || !filename.matches("backup_\\d{8}_\\d{6}(?:_\\d{3})?\\.zip")) {
            throw ApiException.badRequest("备份文件名不正确");
        }
        Path dir = backupDir();
        Path file = dir.resolve(filename).normalize();
        if (!file.startsWith(dir)) {
            throw ApiException.badRequest("备份文件名不正确");
        }
        if (!Files.exists(file)) {
            throw ApiException.notFound("备份文件不存在");
        }
        return file;
    }

    private BackupItem toItem(Path path) {
        try {
            return new BackupItem(
                    path.getFileName().toString(),
                    Files.size(path),
                    Files.getLastModifiedTime(path).toInstant()
            );
        } catch (IOException ex) {
            throw ApiException.badRequest("读取备份文件失败");
        }
    }

    private Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ex) {
            return Instant.EPOCH;
        }
    }

    private Path backupDir() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path root = cwd.getFileName() != null && "backend".equalsIgnoreCase(cwd.getFileName().toString())
                ? cwd.getParent()
                : cwd;
        return root.resolve("backups").resolve("app").normalize();
    }

    private Path newBackupPath(Path dir) {
        for (int i = 0; i < 10; i++) {
            Path target = dir.resolve("backup_" + LocalDateTime.now().format(FILENAME_TIME) + ".zip");
            if (!Files.exists(target)) {
                return target;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ApiException.badRequest("生成备份失败");
            }
        }
        throw ApiException.badRequest("生成备份文件名失败");
    }

    private record TableExport(String name, String sql) {
    }

    private record BackupPayload(Map<String, Object> metadata, Map<String, List<LinkedHashMap<String, Object>>> rows) {
    }

    public record BackupItem(String filename, long size, Instant createdAt) {
    }

    public record BackupFile(String filename, byte[] bytes) {
    }

    public record RestoreResult(BackupItem safetyBackup, Map<String, Integer> restoredRows, int totalRows) {
    }
}
