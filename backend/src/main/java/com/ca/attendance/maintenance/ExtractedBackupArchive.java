package com.ca.attendance.maintenance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class ExtractedBackupArchive implements AutoCloseable {
    private final Path directory;
    private final Map<String, Path> entries;

    ExtractedBackupArchive(Path directory, Map<String, Path> entries) {
        this.directory = directory;
        this.entries = Map.copyOf(new LinkedHashMap<>(entries));
    }

    Path entry(String name) {
        return entries.get(name);
    }

    boolean contains(String name) {
        return entries.containsKey(name);
    }

    Set<String> names() {
        return entries.keySet();
    }

    Path directory() {
        return directory;
    }

    @Override
    public void close() {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Restore staging files are never reused or exposed as backups.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup; a later system temp cleanup can remove leftovers.
        }
    }
}
