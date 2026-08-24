package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class BackupArchiveWriter {
    private final ObjectMapper objectMapper;
    private final BackupCreationLimits limits;

    BackupArchiveWriter(ObjectMapper objectMapper) {
        this(objectMapper, BackupCreationLimits.defaults());
    }

    BackupArchiveWriter(ObjectMapper objectMapper, BackupCreationLimits limits) {
        this.objectMapper = objectMapper;
        this.limits = limits;
    }

    void write(
            Path output,
            Map<String, Object> metadata,
            BackupTableSource exporter,
            String readme
    ) throws IOException {
        try (OutputStream file = Files.newOutputStream(output);
             OutputStream limitedFile = new LimitedArchiveOutputStream(file);
             ZipOutputStream zip = new ZipOutputStream(limitedFile, StandardCharsets.UTF_8)) {
            EntryBudget budget = new EntryBudget();
            writeJson(zip, budget, "metadata.json", metadata);
            exporter.writeTables((table, rows) -> writeRows(zip, budget, table + ".json", rows));
            writeBytes(zip, budget, "README.txt", readme.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void writeJson(
            ZipOutputStream zip,
            EntryBudget budget,
            String name,
            Object value
    ) throws IOException {
        budget.addEntry(name);
        zip.putNextEntry(new ZipEntry(name));
        try {
            JsonGenerator generator = objectMapper.createGenerator(new LimitedEntryOutputStream(zip, budget, name));
            generator.writePOJO(value);
            generator.flush();
        } finally {
            zip.closeEntry();
        }
    }

    private void writeRows(
            ZipOutputStream zip,
            EntryBudget budget,
            String name,
            Stream<Map<String, Object>> rows
    ) throws IOException {
        budget.addEntry(name);
        zip.putNextEntry(new ZipEntry(name));
        try {
            JsonGenerator generator = objectMapper.createGenerator(new LimitedEntryOutputStream(zip, budget, name));
            generator.writeStartArray();
            Iterator<Map<String, Object>> iterator = rows.iterator();
            while (iterator.hasNext()) {
                generator.writePOJO(iterator.next());
            }
            generator.writeEndArray();
            generator.flush();
        } finally {
            zip.closeEntry();
        }
    }

    private void writeBytes(
            ZipOutputStream zip,
            EntryBudget budget,
            String name,
            byte[] bytes
    ) throws IOException {
        budget.addEntry(name);
        zip.putNextEntry(new ZipEntry(name));
        try {
            new LimitedEntryOutputStream(zip, budget, name).write(bytes);
        } finally {
            zip.closeEntry();
        }
    }

    private final class EntryBudget {
        private long totalBytes;
        private int entryCount;

        void addEntry(String entry) {
            entryCount++;
            if (entryCount > limits.maxZipEntries()) {
                throw ApiException.badRequest("备份包含过多条目，无法生成可恢复备份：" + entry);
            }
        }

        void addBytes(String entry, long bytes, long entryBytes) {
            if (entryBytes > limits.maxEntryUncompressedBytes()) {
                throw ApiException.badRequest("备份文件中的 " + entry + " 解压后过大");
            }
            totalBytes += bytes;
            if (totalBytes > limits.maxTotalUncompressedBytes()) {
                throw ApiException.badRequest("备份文件解压后的总数据量过大");
            }
        }
    }

    private final class LimitedEntryOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final EntryBudget budget;
        private final String entry;
        private long entryBytes;

        private LimitedEntryOutputStream(OutputStream delegate, EntryBudget budget, String entry) {
            this.delegate = delegate;
            this.budget = budget;
            this.entry = entry;
        }

        @Override
        public void write(int value) throws IOException {
            add(1);
            delegate.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            add(length);
            delegate.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        private void add(int bytes) {
            entryBytes += bytes;
            budget.addBytes(entry, bytes, entryBytes);
        }
    }

    private final class LimitedArchiveOutputStream extends FilterOutputStream {
        private long bytes;

        private LimitedArchiveOutputStream(OutputStream output) {
            super(output);
        }

        @Override
        public void write(int value) throws IOException {
            add(1);
            out.write(value);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            add(length);
            out.write(buffer, offset, length);
        }

        private void add(int amount) {
            bytes += amount;
            if (bytes > limits.maxArchiveBytes()) {
                throw ApiException.badRequest("备份压缩文件过大，无法生成可恢复备份");
            }
        }
    }
}
