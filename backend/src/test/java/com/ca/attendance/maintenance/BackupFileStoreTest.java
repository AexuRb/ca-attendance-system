package com.ca.attendance.maintenance;

import com.ca.attendance.config.StoragePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                List.of("backup_20260810_120000_001.zip"),
                store.list().stream().map(BackupFileStore.StoredBackup::filename).toList()
        );
    }

    @Test
    void failsCleanlyWhenTheBackupDirectoryCannotBeCreated() throws Exception {
        StoragePaths storagePaths = new StoragePaths(tempDirectory.toString());
        Files.writeString(storagePaths.backupDirectory(), "directory path is occupied by a file");
        BackupFileStore store = new BackupFileStore(storagePaths);

        assertThrows(RuntimeException.class, store::prepare);
        assertEquals(List.of(), store.list());
    }

    @Test
    void publishAutomaticallyKeepsOnlyTheNewestBackupsWithinCountLimit() throws Exception {
        StoragePaths storagePaths = new StoragePaths(tempDirectory.toString());
        Path backupDirectory = storagePaths.backupDirectory();
        Files.createDirectories(backupDirectory);
        Path oldest = writeBackup(backupDirectory, "backup_20260810_120000_001.zip", "oldest", 1);
        Path previous = writeBackup(backupDirectory, "backup_20260810_120001_001.zip", "previous", 2);
        BackupFileStore store = new BackupFileStore(storagePaths, new BackupRetentionPolicy(2, 1024));

        BackupFileStore.PendingBackup pending = store.prepare();
        Files.writeString(pending.temporary(), "newest");
        store.publish(pending);

        List<String> filenames = store.list().stream().map(BackupFileStore.StoredBackup::filename).toList();
        assertEquals(2, filenames.size());
        assertTrue(filenames.contains(pending.target().getFileName().toString()));
        assertTrue(filenames.contains(previous.getFileName().toString()));
        assertTrue(Files.notExists(oldest));
    }

    @Test
    void publishKeepsNewestBackupWhenItAloneExceedsTheSpaceLimit() throws Exception {
        StoragePaths storagePaths = new StoragePaths(tempDirectory.toString());
        Path backupDirectory = storagePaths.backupDirectory();
        Files.createDirectories(backupDirectory);
        Path old = writeBackup(backupDirectory, "backup_20260810_120000_001.zip", "old", 1);
        BackupFileStore store = new BackupFileStore(storagePaths, new BackupRetentionPolicy(10, 3));

        BackupFileStore.PendingBackup pending = store.prepare();
        Files.writeString(pending.temporary(), "newest-is-larger-than-limit");
        store.publish(pending);

        assertEquals(
                List.of(pending.target().getFileName().toString()),
                store.list().stream().map(BackupFileStore.StoredBackup::filename).toList()
        );
        assertTrue(Files.notExists(old));
    }

    @Test
    void publishAlwaysProtectsTheNewFileWhenAnOldBackupHasAFutureTimestamp() throws Exception {
        StoragePaths storagePaths = new StoragePaths(tempDirectory.toString());
        Path backupDirectory = storagePaths.backupDirectory();
        Files.createDirectories(backupDirectory);
        Path futureDated = writeBackup(
                backupDirectory,
                "backup_20260810_120000_001.zip",
                "future-dated",
                86400
        );
        Files.setLastModifiedTime(futureDated, FileTime.from(Instant.now().plusSeconds(86400)));
        BackupFileStore store = new BackupFileStore(storagePaths, new BackupRetentionPolicy(1, 1024));

        BackupFileStore.PendingBackup pending = store.prepare();
        Files.writeString(pending.temporary(), "published-now");
        store.publish(pending);

        assertEquals(
                List.of(pending.target().getFileName().toString()),
                store.list().stream().map(BackupFileStore.StoredBackup::filename).toList()
        );
        assertTrue(Files.notExists(futureDated));
    }

    @Test
    void listSkipsARecognizedBackupThatDisappearsDuringInspection() throws Exception {
        StoragePaths storagePaths = new StoragePaths(tempDirectory.toString());
        Path backupDirectory = storagePaths.backupDirectory();
        Files.createDirectories(backupDirectory);
        Path disappearing = writeBackup(
                backupDirectory,
                "backup_20260810_120000_001.zip",
                "temporary",
                1
        );
        BackupFileStore store = new BackupFileStore(storagePaths);
        Files.delete(disappearing);

        assertTrue(store.describeIfPresent(disappearing).isEmpty());
        assertEquals(List.of(), store.list());
    }

    @Test
    void failedPublishDoesNotExposeAPartialBackupAndDiscardRemovesTemporaryFile() throws Exception {
        StoragePaths storagePaths = new StoragePaths(tempDirectory.toString());
        BackupFileStore store = new BackupFileStore(storagePaths);
        BackupFileStore.PendingBackup pending = store.prepare();
        Files.writeString(pending.temporary(), "partial");
        Files.delete(pending.temporary());

        assertThrows(java.io.IOException.class, () -> store.publish(pending));
        store.discard(pending);

        assertTrue(Files.notExists(pending.temporary()));
        assertTrue(Files.notExists(pending.target()));
        assertEquals(List.of(), store.list());
    }

    private Path writeBackup(Path directory, String filename, String content, long second) throws Exception {
        Path file = directory.resolve(filename);
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2026-08-10T12:00:00Z").plusSeconds(second)));
        return file;
    }
}
