package com.ca.attendance.maintenance;

import com.ca.attendance.access.RolePermissionPolicy;
import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.auth.TokenService;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.config.StoragePaths;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BackupService {
    private static final String BACKUP_README = """
            计算机协会本地管理系统数据备份

            本备份由系统后台生成，包含成员、签到、培训、排班、维修、日志和设置等核心数据。
            请勿把包含真实成员信息的备份文件上传到公开仓库。
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final TokenService tokenService;
    private final DatabaseBackupExporter exporter;
    private final BackupArchiveReader archiveReader;
    private final BackupArchiveWriter archiveWriter;
    private final BackupRestoreValidator restoreValidator;
    private final DatabaseRestoreExecutor restoreExecutor;
    private final BackupFileStore files;

    public BackupService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            TokenService tokenService,
            StoragePaths storagePaths
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.transactions = transactionTemplate;
        this.tokenService = tokenService;
        this.exporter = new DatabaseBackupExporter(jdbc, transactionTemplate);
        this.archiveReader = new BackupArchiveReader();
        this.archiveWriter = new BackupArchiveWriter(objectMapper);
        this.restoreValidator = new BackupRestoreValidator(objectMapper);
        this.restoreExecutor = new DatabaseRestoreExecutor(jdbc, objectMapper);
        this.files = new BackupFileStore(storagePaths);
    }

    public BackupItem create() {
        AuthUser current = AuthContext.current();
        RolePermissionPolicy.require(
                current.role(),
                RolePermissionPolicy.Permission.BACKUPS,
                "只有会长或管理员可以备份数据"
        );
        return createBackup(current.studentNo(), current.name(), "后台手动备份");
    }

    public BackupItem createSystemBackup(String reason) {
        return createBackup("LOCAL_SYSTEM", "本机系统", reason);
    }

    public List<BackupItem> list() {
        RolePermissionPolicy.require(
                AuthContext.current().role(),
                RolePermissionPolicy.Permission.BACKUPS,
                "只有会长或管理员可以查看备份"
        );
        return files.list().stream().map(this::toItem).toList();
    }

    public BackupFile download(String filename) {
        RolePermissionPolicy.require(
                AuthContext.current().role(),
                RolePermissionPolicy.Permission.BACKUPS,
                "只有会长或管理员可以下载备份"
        );
        BackupFileStore.StoredFile stored = files.read(filename);
        return new BackupFile(stored.filename(), stored.bytes());
    }

    public void delete(String filename) {
        RolePermissionPolicy.require(
                AuthContext.current().role(),
                RolePermissionPolicy.Permission.BACKUP_ADMIN,
                "只有管理员可以删除备份"
        );
        files.delete(filename);
    }

    public RestoreResult restore(MultipartFile file) {
        AuthUser current = AuthContext.current();
        RolePermissionPolicy.require(
                current.role(),
                RolePermissionPolicy.Permission.BACKUP_ADMIN,
                "只有管理员可以恢复备份"
        );
        Map<String, byte[]> entries = archiveReader.readEntries(file, restoreValidator.supportedEntries());
        BackupRestorePayload payload = restoreValidator.parse(entries);
        BackupItem safetyBackup = create();

        RestoreResult result = transactions.execute(status -> {
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

    private synchronized BackupItem createBackup(
            String operatorStudentNo,
            String operatorName,
            String reason
    ) {
        BackupFileStore.PendingBackup pending = null;
        try {
            pending = files.prepare();
            archiveWriter.write(
                    pending.temporary(),
                    metadata(operatorStudentNo, operatorName, reason),
                    exporter.capture(),
                    BACKUP_README
            );
            files.publish(pending);
            return toItem(files.describe(pending.target()));
        } catch (RuntimeException ex) {
            files.discard(pending);
            throw ex;
        } catch (IOException ex) {
            files.discard(pending);
            throw ApiException.badRequest("生成备份失败");
        }
    }

    private RestoreResult restorePayload(
            BackupRestorePayload payload,
            BackupItem safetyBackup,
            AuthUser current
    ) {
        Map<String, Integer> restoredRows = restoreExecutor.restore(payload);
        RestoreResult result = new RestoreResult(
                safetyBackup,
                restoredRows,
                restoredRows.values().stream().mapToInt(Integer::intValue).sum()
        );
        logRestore(current, result);
        return result;
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

    private Map<String, Object> metadata(
            String operatorStudentNo,
            String operatorName,
            String reason
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("system", "计算机协会本地管理系统");
        metadata.put("database", "SQLite");
        metadata.put("schemaVersion", BackupSchema.SCHEMA_VERSION);
        metadata.put("createdAt", LocalDateTime.now());
        metadata.put("operatorStudentNo", operatorStudentNo);
        metadata.put("operatorName", operatorName);
        metadata.put("reason", reason);
        metadata.put("tables", exporter.tableNames());
        return metadata;
    }

    private BackupItem toItem(BackupFileStore.StoredBackup stored) {
        return new BackupItem(stored.filename(), stored.size(), stored.createdAt());
    }

    public record BackupItem(String filename, long size, Instant createdAt) {
    }

    public record BackupFile(String filename, byte[] bytes) {
    }

    public record RestoreResult(
            BackupItem safetyBackup,
            Map<String, Integer> restoredRows,
            int totalRows
    ) {
    }
}
