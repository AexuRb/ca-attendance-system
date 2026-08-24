package com.ca.attendance.maintenance;

import com.ca.attendance.common.ApiException;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupArchiveWriterTest {
    @TempDir
    Path tempDirectory;

    @Test
    void createsTablesThatExceedTheLegacyRestoreRowLimit() throws Exception {
        BackupTableSource source = writer -> writer.write(
                "attendance_records",
                IntStream.range(0, 100_001)
                        .mapToObj(index -> Map.<String, Object>of("id", index))
        );
        Path output = tempDirectory.resolve("large-table.zip.tmp");

        assertDoesNotThrow(() -> new BackupArchiveWriter(new ObjectMapper()).write(
                output,
                Map.of("schemaVersion", BackupSchema.SCHEMA_VERSION),
                source,
                "test"
        ));

        assertTrue(Files.size(output) > 0);
    }

    @Test
    void rejectsArchivesThatExceedTheCreationEntryLimit() {
        BackupTableSource source = writer -> {
            for (int index = 0; index < BackupCreationLimits.defaults().maxZipEntries(); index++) {
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

    @Test
    void enforcesCreationCapacityIndependentlyFromRestoreCapacity() {
        BackupCreationLimits limits = new BackupCreationLimits(1024, 4, 8, 16);
        BackupTableSource source = writer -> writer.write(
                "attendance_records",
                java.util.stream.Stream.of(Map.of("value", "123456789"))
        );

        ApiException error = assertThrows(ApiException.class, () -> new BackupArchiveWriter(
                new ObjectMapper(),
                limits
        ).write(
                tempDirectory.resolve("creation-limit.zip.tmp"),
                Map.of("schemaVersion", BackupSchema.SCHEMA_VERSION),
                source,
                "test"
        ));

        assertTrue(error.getMessage().contains("解压后过大"));
    }
}
