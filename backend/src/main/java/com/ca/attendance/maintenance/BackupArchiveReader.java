package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class BackupArchiveReader {
    private final Path temporaryRoot;

    BackupArchiveReader() {
        this(Path.of(System.getProperty("java.io.tmpdir")));
    }

    BackupArchiveReader(Path temporaryRoot) {
        this.temporaryRoot = temporaryRoot;
    }

    ExtractedBackupArchive extract(MultipartFile file, Set<String> supportedEntries) {
        validateUpload(file);
        Path directory = createTemporaryDirectory();
        Map<String, Path> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            int entryCount = 0;
            long totalBytes = 0;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = validateEntry(entry, supportedEntries, entries, ++entryCount);
                Path output = directory.resolve(name);
                totalBytes = extractEntry(zip, output, name, buffer, totalBytes);
                entries.put(name, output);
                zip.closeEntry();
            }
            return new ExtractedBackupArchive(directory, entries);
        } catch (ApiException ex) {
            new ExtractedBackupArchive(directory, entries).close();
            throw ex;
        } catch (IOException ex) {
            new ExtractedBackupArchive(directory, entries).close();
            throw ApiException.badRequest("备份文件读取失败");
        }
    }

    private long extractEntry(
            ZipInputStream zip,
            Path output,
            String name,
            byte[] buffer,
            long previousTotal
    ) throws IOException {
        long entrySize = 0;
        long totalSize = previousTotal;
        try (var fileOutput = Files.newOutputStream(output)) {
            int read;
            while ((read = zip.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                entrySize += read;
                totalSize += read;
                if (entrySize > BackupArchiveLimits.MAX_ENTRY_UNCOMPRESSED_BYTES) {
                    throw ApiException.badRequest("备份文件中的 " + name + " 解压后过大");
                }
                if (totalSize > BackupArchiveLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw ApiException.badRequest("备份文件解压后的总数据量过大");
                }
                fileOutput.write(buffer, 0, read);
            }
        }
        return totalSize;
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择备份 zip 文件");
        }
        if (file.getSize() > BackupArchiveLimits.MAX_ARCHIVE_BYTES) {
            throw ApiException.badRequest("备份文件过大，请确认是否为系统生成的备份");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".zip")) {
            throw ApiException.badRequest("只能上传系统生成的 zip 备份文件");
        }
    }

    private String validateEntry(
            ZipEntry entry,
            Set<String> supportedEntries,
            Map<String, Path> entries,
            int entryCount
    ) {
        if (entryCount > BackupArchiveLimits.MAX_ZIP_ENTRIES) {
            throw ApiException.badRequest("备份文件包含过多条目");
        }
        if (entry.isDirectory()) {
            throw ApiException.badRequest("备份文件结构不正确");
        }
        String name = entry.getName();
        if (name == null || name.isBlank() || name.contains("\\") || name.contains("/")) {
            throw ApiException.badRequest("备份文件结构不正确");
        }
        if (!supportedEntries.contains(name)) {
            throw ApiException.badRequest("备份文件包含不支持的条目：" + name);
        }
        if (entries.containsKey(name)) {
            throw ApiException.badRequest("备份文件包含重复条目：" + name);
        }
        if (entry.getSize() > BackupArchiveLimits.MAX_ENTRY_UNCOMPRESSED_BYTES) {
            throw ApiException.badRequest("备份文件中的 " + name + " 解压后过大");
        }
        return name;
    }

    private Path createTemporaryDirectory() {
        try {
            Files.createDirectories(temporaryRoot);
            return Files.createTempDirectory(temporaryRoot, "ca-attendance-restore-");
        } catch (IOException ex) {
            throw ApiException.badRequest("无法准备备份恢复临时目录");
        }
    }
}
