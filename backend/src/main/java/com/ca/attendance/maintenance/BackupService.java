package com.ca.attendance.maintenance;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class BackupService {
    private static final DateTimeFormatter FILENAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final List<TableExport> TABLES = List.of(
            new TableExport("users", "SELECT * FROM users ORDER BY id"),
            new TableExport("attendance_records", "SELECT * FROM attendance_records ORDER BY duty_date DESC, check_in_time DESC, id DESC"),
            new TableExport("operation_logs", "SELECT * FROM operation_logs ORDER BY created_at DESC, id DESC"),
            new TableExport("duty_weekday_settings", "SELECT * FROM duty_weekday_settings ORDER BY weekday")
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public BackupService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public BackupItem create() {
        AuthUser current = AuthContext.current();
        if (current.role() != Role.PRESIDENT && current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有会长或管理员可以备份数据");
        }

        try {
            Path dir = backupDir();
            Files.createDirectories(dir);
            String filename = "backup_" + LocalDateTime.now().format(FILENAME_TIME) + ".zip";
            Path target = dir.resolve(filename);

            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
                writeJson(zip, "metadata.json", metadata(current));
                for (TableExport table : TABLES) {
                    writeJson(zip, table.name() + ".json", jdbc.queryForList(table.sql()));
                }
                writeText(zip, "README.txt", """
                        计算机协会签到签退系统数据备份

                        本备份由系统后台生成，包含 users、attendance_records、operation_logs、duty_weekday_settings 四张业务表。
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
        if (filename == null || !filename.matches("backup_\\d{8}_\\d{6}\\.zip")) {
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

    private record TableExport(String name, String sql) {
    }

    public record BackupItem(String filename, long size, Instant createdAt) {
    }

    public record BackupFile(String filename, byte[] bytes) {
    }
}
