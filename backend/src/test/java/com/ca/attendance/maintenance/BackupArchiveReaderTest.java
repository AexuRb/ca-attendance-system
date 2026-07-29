package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupArchiveReaderTest {
    private final BackupArchiveReader reader = new BackupArchiveReader();

    @Test
    void readsOnlySupportedTopLevelEntries() throws Exception {
        byte[] metadata = "{}".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile upload = zipUpload(Map.of("metadata.json", metadata));

        Map<String, byte[]> entries = reader.readEntries(upload, Set.of("metadata.json"));

        assertArrayEquals(metadata, entries.get("metadata.json"));
    }

    @Test
    void rejectsUnsupportedEntries() throws Exception {
        MockMultipartFile upload = zipUpload(Map.of("nested/users.json", "[]".getBytes(StandardCharsets.UTF_8)));

        ApiException error = assertThrows(
                ApiException.class,
                () -> reader.readEntries(upload, Set.of("users.json"))
        );

        assertTrue(error.getMessage().contains("结构不正确"));
    }

    private MockMultipartFile zipUpload(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", "backup_test.zip", "application/zip", output.toByteArray());
    }
}
