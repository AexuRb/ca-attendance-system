package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupArchiveWriterTest {
    @TempDir
    Path tempDirectory;

    @Test
    void rejectsTablesThatExceedTheSharedRestoreRowLimit() throws Exception {
        BackupTableSource source = writer -> writer.write(
                "attendance_records",
                IntStream.rangeClosed(0, BackupArchiveLimits.MAX_ROWS_PER_TABLE)
                        .mapToObj(index -> Map.<String, Object>of("id", index))
        );
        Path output = tempDirectory.resolve("too-many-rows.zip.tmp");

        ApiException error = assertThrows(ApiException.class, () -> new BackupArchiveWriter(new ObjectMapper()).write(
                output,
                Map.of("schemaVersion", BackupSchema.SCHEMA_VERSION),
                source,
                "test"
        ));

        assertTrue(error.getMessage().contains("数据行数过多"));
    }

    @Test
    void rejectsArchivesThatExceedTheSharedEntryLimit() {
        BackupTableSource source = writer -> {
            for (int index = 0; index < BackupArchiveLimits.MAX_ZIP_ENTRIES; index++) {
                writer.write("table_" + index, java.util.stream.Stream.empty());
            }
        };

        ApiException error = assertThrows(ApiException.class, () -> new BackupArchiveWriter(new ObjectMapper()).write(
                tempDirectory.resolve("too-many-entries.zip.tmp"),
                Map.of("schemaVersion", BackupSchema.SCHEMA_VERSION),
                source,
                "test"
        ));

        assertTrue(error.getMessage().contains("过多条目"));
    }
}
