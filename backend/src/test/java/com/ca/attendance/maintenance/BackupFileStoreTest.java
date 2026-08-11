package com.ca.attendance.maintenance;

import com.ca.attendance.config.StoragePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackupFileStoreTest {
    @TempDir
    Path tempDirectory;

    @Test
    void listsOnlyBackupsCreatedWithTheRecognizedFilename() throws Exception {
        StoragePaths storagePaths = new StoragePaths(tempDirectory.toString());
        Path backupDirectory = storagePaths.backupDirectory();
        Files.createDirectories(backupDirectory);
        Files.writeString(backupDirectory.resolve("backup_20260810_120000_001.zip"), "valid");
        Files.writeString(backupDirectory.resolve("renamed-or-incomplete.zip"), "invalid");

        BackupFileStore store = new BackupFileStore(storagePaths);

        assertEquals(
                java.util.List.of("backup_20260810_120000_001.zip"),
                store.list().stream().map(BackupFileStore.StoredBackup::filename).toList()
        );
    }

    @Test
    void failsCleanlyWhenTheBackupDirectoryCannotBeCreated() throws Exception {
        StoragePaths storagePaths = new StoragePaths(tempDirectory.toString());
        Files.writeString(storagePaths.backupDirectory(), "directory path is occupied by a file");
        BackupFileStore store = new BackupFileStore(storagePaths);

        assertThrows(RuntimeException.class, store::prepare);
        assertEquals(java.util.List.of(), store.list());
    }
}
