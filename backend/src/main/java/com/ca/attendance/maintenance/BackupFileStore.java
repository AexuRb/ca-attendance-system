package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.config.StoragePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class BackupFileStore {
    private static final Logger logger = LoggerFactory.getLogger(BackupFileStore.class);
    private static final DateTimeFormatter FILENAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final String FILENAME_PATTERN = "backup_\\d{8}_\\d{6}(?:_\\d{3})?\\.zip";

    private final StoragePaths storagePaths;
    private final BackupRetentionPolicy retentionPolicy;

    BackupFileStore(StoragePaths storagePaths) {
        this(storagePaths, BackupRetentionPolicy.defaults());
    }

    BackupFileStore(StoragePaths storagePaths, BackupRetentionPolicy retentionPolicy) {
        this.storagePaths = storagePaths;
        this.retentionPolicy = retentionPolicy;
    }

    synchronized PendingBackup prepare() {
        try {
            Path directory = directory();
            Files.createDirectories(directory);
            Path target = newBackupPath(directory);
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            return new PendingBackup(temporary, target);
        } catch (IOException ex) {
            throw ApiException.badRequest("生成备份失败");
        }
    }

    void publish(PendingBackup pending) throws IOException {
        try {
            Files.move(pending.temporary(), pending.target(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(pending.temporary(), pending.target());
        }
        try {
            enforceRetention(pending.target());
        } catch (RuntimeException ex) {
            logger.warn("备份已生成，但自动清理旧备份失败，将在下次备份时重试", ex);
        }
    }

    void discard(PendingBackup pending) {
        if (pending == null) {
            return;
        }
        try {
            Files.deleteIfExists(pending.temporary());
        } catch (IOException ignored) {
            // Temporary files are never listed as restorable backups.
        }
    }

    List<StoredBackup> list() {
        try {
            Path directory = directory();
            if (!Files.isDirectory(directory)) {
                return List.of();
            }
            try (var stream = Files.list(directory)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().matches(FILENAME_PATTERN))
                        .sorted(Comparator.comparing(this::lastModified).reversed())
                        .map(this::describeIfPresent)
                        .flatMap(Optional::stream)
                        .toList();
            }
        } catch (IOException ex) {
            throw ApiException.badRequest("读取备份列表失败");
        }
    }

    StoredFile open(String filename) {
        Path file = resolve(filename);
        try {
            return new StoredFile(file.getFileName().toString(), Files.size(file), file);
        } catch (IOException ex) {
            throw ApiException.notFound("备份文件不存在");
        }
    }

    void delete(String filename) {
        Path file = resolve(filename);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw ApiException.badRequest("删除备份失败");
        }
    }

    StoredBackup describe(Path path) {
        try {
            return new StoredBackup(
                    path.getFileName().toString(),
                    Files.size(path),
                    Files.getLastModifiedTime(path).toInstant()
            );
        } catch (IOException ex) {
            throw ApiException.badRequest("读取备份文件失败");
        }
    }

    Optional<StoredBackup> describeIfPresent(Path path) {
        try {
            return Optional.of(new StoredBackup(
                    path.getFileName().toString(),
                    Files.size(path),
                    Files.getLastModifiedTime(path).toInstant()
            ));
        } catch (NoSuchFileException ex) {
            return Optional.empty();
        } catch (IOException ex) {
            if (Files.notExists(path)) {
                return Optional.empty();
            }
            logger.warn("跳过无法读取的备份文件：{}", path.getFileName(), ex);
            return Optional.empty();
        }
    }

    RetentionResult enforceRetention() {
        return enforceRetention(null);
    }

    private RetentionResult enforceRetention(Path protectedBackup) {
        List<StoredBackup> backups = new java.util.ArrayList<>(list());
        if (protectedBackup != null) {
            String protectedFilename = protectedBackup.getFileName().toString();
            backups.stream()
                    .filter(backup -> protectedFilename.equals(backup.filename()))
                    .findFirst()
                    .ifPresent(backup -> {
                        backups.remove(backup);
                        backups.addFirst(backup);
                    });
        }
        int kept = 0;
        long keptBytes = 0;
        int deleted = 0;
        boolean capacityReached = false;
        java.util.ArrayList<String> failures = new java.util.ArrayList<>();
        for (StoredBackup backup : backups) {
            boolean newest = kept == 0;
            boolean withinCount = kept < retentionPolicy.maxFiles();
            boolean withinSpace = backup.size() <= retentionPolicy.maxTotalBytes() - keptBytes;
            if (!capacityReached && (newest || (withinCount && withinSpace))) {
                kept++;
                keptBytes += backup.size();
                if (kept >= retentionPolicy.maxFiles() || keptBytes >= retentionPolicy.maxTotalBytes()) {
                    capacityReached = true;
                }
                continue;
            }
            capacityReached = true;
            try {
                Files.deleteIfExists(directory().resolve(backup.filename()));
                deleted++;
            } catch (IOException ex) {
                failures.add(backup.filename());
                logger.warn("自动清理旧备份失败：{}", backup.filename(), ex);
            }
        }
        return new RetentionResult(deleted, List.copyOf(failures));
    }

    private Path resolve(String filename) {
        if (filename == null || !filename.matches(FILENAME_PATTERN)) {
            throw ApiException.badRequest("备份文件名不正确");
        }
        Path directory = directory();
        Path file = directory.resolve(filename).normalize();
        if (!file.startsWith(directory)) {
            throw ApiException.badRequest("备份文件名不正确");
        }
        if (!Files.exists(file)) {
            throw ApiException.notFound("备份文件不存在");
        }
        return file;
    }

    private Path newBackupPath(Path directory) {
        for (int attempt = 0; attempt < 10; attempt++) {
            Path target = directory.resolve("backup_" + LocalDateTime.now().format(FILENAME_TIME) + ".zip");
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

    private Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ex) {
            return Instant.EPOCH;
        }
    }

    private Path directory() {
        return storagePaths.backupDirectory();
    }

    record PendingBackup(Path temporary, Path target) {
    }

    record StoredBackup(String filename, long size, Instant createdAt) {
    }

    record StoredFile(String filename, long size, Path path) {
    }

    record RetentionResult(int deleted, List<String> failures) {
    }
}
