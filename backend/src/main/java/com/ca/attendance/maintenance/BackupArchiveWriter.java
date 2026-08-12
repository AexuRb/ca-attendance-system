package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    BackupArchiveWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
            ArchiveBudget budget = new ArchiveBudget();
            writeJson(zip, budget, "metadata.json", metadata);
            exporter.writeTables((table, rows) -> writeRows(zip, budget, table + ".json", table, rows));
            writeBytes(zip, budget, "README.txt", readme.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void writeJson(
            ZipOutputStream zip,
            ArchiveBudget budget,
            String name,
            Object value
    ) throws IOException {
        budget.addEntry(name);
        zip.putNextEntry(new ZipEntry(name));
        try {
            OutputStream entry = new LimitedEntryOutputStream(zip, budget, name);
            JsonGenerator generator = objectMapper.getFactory().createGenerator(entry);
            generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
            try (generator) {
                generator.writeObject(value);
            }
        } finally {
            zip.closeEntry();
        }
    }

    private void writeRows(
            ZipOutputStream zip,
            ArchiveBudget budget,
            String name,
            String table,
            Stream<Map<String, Object>> rows
    ) throws IOException {
        budget.addEntry(name);
        zip.putNextEntry(new ZipEntry(name));
        try {
            OutputStream entry = new LimitedEntryOutputStream(zip, budget, name);
            JsonGenerator generator = objectMapper.getFactory().createGenerator(entry);
            generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
            try (generator) {
                generator.writeStartArray();
                Iterator<Map<String, Object>> iterator = rows.iterator();
                while (iterator.hasNext()) {
                    budget.addRow(table);
                    generator.writeObject(iterator.next());
                }
                generator.writeEndArray();
            }
        } finally {
            zip.closeEntry();
        }
    }

    private void writeBytes(
            ZipOutputStream zip,
            ArchiveBudget budget,
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

    private static final class ArchiveBudget {
        private long totalBytes;
        private int tableRows;
        private String currentTable;
        private int totalRows;
        private int entryCount;

        void addEntry(String entry) {
            entryCount++;
            if (entryCount > BackupArchiveLimits.MAX_ZIP_ENTRIES) {
                throw ApiException.badRequest("备份包含过多条目，无法生成可恢复备份：" + entry);
            }
        }

        void addBytes(String entry, long bytes, long entryBytes) {
            if (entryBytes > BackupArchiveLimits.MAX_ENTRY_UNCOMPRESSED_BYTES) {
                throw ApiException.badRequest("备份文件中的 " + entry + " 解压后过大");
            }
            totalBytes += bytes;
            if (totalBytes > BackupArchiveLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                throw ApiException.badRequest("备份文件解压后的总数据量过大");
            }
        }

        void addRow(String table) {
            if (!table.equals(currentTable)) {
                currentTable = table;
                tableRows = 0;
            }
            tableRows++;
            totalRows++;
            if (tableRows > BackupArchiveLimits.MAX_ROWS_PER_TABLE) {
                throw ApiException.badRequest(table + " 数据行数过多，无法生成可恢复备份");
            }
            if (totalRows > BackupArchiveLimits.MAX_TOTAL_ROWS) {
                throw ApiException.badRequest("数据库总行数过多，无法生成可恢复备份");
            }
        }
    }

    private static final class LimitedEntryOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final ArchiveBudget budget;
        private final String entry;
        private long entryBytes;

        private LimitedEntryOutputStream(OutputStream delegate, ArchiveBudget budget, String entry) {
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

    private static final class LimitedArchiveOutputStream extends FilterOutputStream {
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
            if (bytes > BackupArchiveLimits.MAX_ARCHIVE_BYTES) {
                throw ApiException.badRequest("备份压缩文件过大，无法生成可恢复备份");
            }
        }
    }
}
