package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class BackupArchiveReader {
    private static final long MAX_RESTORE_BYTES = 50L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 16;
    private static final long MAX_ENTRY_UNCOMPRESSED_BYTES = 16L * 1024 * 1024;
    private static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 64L * 1024 * 1024;

    Map<String, byte[]> readEntries(MultipartFile file, Set<String> supportedEntries) {
        validateUpload(file);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            int entryCount = 0;
            long totalBytes = 0;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = validateEntry(entry, supportedEntries, entries, ++entryCount);
                EntryBytes entryBytes = readEntry(zip, name, buffer, totalBytes);
                totalBytes = entryBytes.totalBytes();
                entries.put(name, entryBytes.bytes());
                zip.closeEntry();
            }
            return entries;
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw ApiException.badRequest("备份文件读取失败");
        }
    }

    private EntryBytes readEntry(ZipInputStream zip, String name, byte[] buffer, long previousTotal) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long entrySize = 0;
        long totalSize = previousTotal;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            if (read == 0) {
                continue;
            }
            entrySize += read;
            totalSize += read;
            if (entrySize > MAX_ENTRY_UNCOMPRESSED_BYTES) {
                throw ApiException.badRequest("备份文件中的 " + name + " 解压后过大");
            }
            if (totalSize > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                throw ApiException.badRequest("备份文件解压后的总数据量过大");
            }
            output.write(buffer, 0, read);
        }
        return new EntryBytes(output.toByteArray(), totalSize);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择备份 zip 文件");
        }
        if (file.getSize() > MAX_RESTORE_BYTES) {
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
            Map<String, byte[]> entries,
            int entryCount
    ) {
        if (entryCount > MAX_ZIP_ENTRIES) {
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
        if (entry.getSize() > MAX_ENTRY_UNCOMPRESSED_BYTES) {
            throw ApiException.badRequest("备份文件中的 " + name + " 解压后过大");
        }
        return name;
    }

    private record EntryBytes(byte[] bytes, long totalBytes) {
    }
}
