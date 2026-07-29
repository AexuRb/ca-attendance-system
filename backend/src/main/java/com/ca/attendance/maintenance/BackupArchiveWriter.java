package com.ca.attendance.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class BackupArchiveWriter {
    private final ObjectMapper objectMapper;

    BackupArchiveWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(
            Path output,
            Map<String, Object> metadata,
            Map<String, List<Map<String, Object>>> tables,
            String readme
    ) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output), StandardCharsets.UTF_8)) {
            writeJson(zip, "metadata.json", metadata);
            for (Map.Entry<String, List<Map<String, Object>>> table : tables.entrySet()) {
                writeJson(zip, table.getKey() + ".json", table.getValue());
            }
            writeBytes(zip, "README.txt", readme.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void writeJson(ZipOutputStream zip, String name, Object value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output, value);
        writeBytes(zip, name, output.toByteArray());
    }

    private void writeBytes(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }
}
