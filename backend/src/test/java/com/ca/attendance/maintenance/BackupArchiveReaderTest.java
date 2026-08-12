package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupArchiveReaderTest {
    @TempDir
    Path tempDirectory;

    private BackupArchiveReader reader;

    @BeforeEach
    void setUp() {
        reader = new BackupArchiveReader(tempDirectory);
    }

    @Test
    void readsOnlySupportedTopLevelEntries() throws Exception {
        byte[] metadata = "{}".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile upload = zipUpload(Map.of("metadata.json", metadata));

        Path extractedDirectory;
        try (ExtractedBackupArchive archive = reader.extract(upload, Set.of("metadata.json"))) {
            extractedDirectory = archive.directory();
            assertArrayEquals(metadata, Files.readAllBytes(archive.entry("metadata.json")));
            assertTrue(Files.isDirectory(extractedDirectory));
        }
        assertTrue(Files.notExists(extractedDirectory));
    }

    @Test
    void rejectsUnsupportedEntries() throws Exception {
        MockMultipartFile upload = zipUpload(Map.of("nested/users.json", "[]".getBytes(StandardCharsets.UTF_8)));

        ApiException error = assertThrows(
                ApiException.class,
                () -> reader.extract(upload, Set.of("users.json"))
        );

        assertTrue(error.getMessage().contains("结构不正确"));
    }

    @Test
    void rejectsUnknownTopLevelEntries() throws Exception {
        MockMultipartFile upload = zipUpload(Map.of("unknown.json", "[]".getBytes(StandardCharsets.UTF_8)));

        ApiException error = assertThrows(
                ApiException.class,
                () -> reader.extract(upload, Set.of("users.json"))
        );

        assertTrue(error.getMessage().contains("不支持的条目"));
    }

    @Test
    void rejectsDuplicateEntries() throws Exception {
        List<Map.Entry<String, byte[]>> entries = List.of(
                Map.entry("metadata.json", "{}".getBytes(StandardCharsets.UTF_8)),
                Map.entry("metadatz.json", "{}".getBytes(StandardCharsets.UTF_8))
        );
        byte[] duplicateArchive = replaceAscii(zipUpload(entries).getBytes(), "metadatz.json", "metadata.json");
        MockMultipartFile upload = new MockMultipartFile(
                "file", "backup_duplicate.zip", "application/zip", duplicateArchive
        );

        ApiException error = assertThrows(
                ApiException.class,
                () -> reader.extract(upload, Set.of("metadata.json"))
        );

        assertTrue(error.getMessage().contains("重复条目"));
    }

    @Test
    void rejectsArchivesWithTooManyEntries() throws Exception {
        List<Map.Entry<String, byte[]>> entries = new ArrayList<>();
        Set<String> supported = new java.util.LinkedHashSet<>();
        for (int index = 0; index < 17; index++) {
            String name = "entry-" + index + ".json";
            entries.add(Map.entry(name, "[]".getBytes(StandardCharsets.UTF_8)));
            supported.add(name);
        }

        ApiException error = assertThrows(
                ApiException.class,
                () -> reader.extract(zipUpload(entries), supported)
        );

        assertTrue(error.getMessage().contains("过多条目"));
        try (var children = Files.list(tempDirectory)) {
            assertTrue(children.findAny().isEmpty(), "失败的解压不应遗留临时目录");
        }
    }

    private MockMultipartFile zipUpload(Map<String, byte[]> entries) throws Exception {
        return zipUpload(entries.entrySet().stream().toList());
    }

    private MockMultipartFile zipUpload(List<Map.Entry<String, byte[]>> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", "backup_test.zip", "application/zip", output.toByteArray());
    }

    private byte[] replaceAscii(byte[] source, String from, String to) {
        byte[] fromBytes = from.getBytes(StandardCharsets.US_ASCII);
        byte[] toBytes = to.getBytes(StandardCharsets.US_ASCII);
        if (fromBytes.length != toBytes.length) {
            throw new IllegalArgumentException("ZIP entry names must have the same byte length");
        }
        for (int index = 0; index <= source.length - fromBytes.length; index++) {
            boolean matches = true;
            for (int offset = 0; offset < fromBytes.length; offset++) {
                if (source[index + offset] != fromBytes[offset]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                System.arraycopy(toBytes, 0, source, index, toBytes.length);
            }
        }
        return source;
    }
}
