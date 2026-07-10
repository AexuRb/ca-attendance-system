package com.ca.attendance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class StoragePaths {
    private final Path root;
    private final Path dataDirectory;
    private final Path backupDirectory;
    private final Path exportDirectory;
    private final Path logDirectory;

    public StoragePaths(@Value("${app.storage.root:.}") String configuredRoot) {
        this.root = Path.of(configuredRoot).toAbsolutePath().normalize();
        this.dataDirectory = root.resolve("data");
        this.backupDirectory = root.resolve("backups");
        this.exportDirectory = root.resolve("exports");
        this.logDirectory = root.resolve("logs");
        createDirectories();
    }

    private void createDirectories() {
        try {
            Files.createDirectories(dataDirectory);
            Files.createDirectories(backupDirectory);
            Files.createDirectories(exportDirectory);
            Files.createDirectories(logDirectory);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建应用数据目录：" + root, ex);
        }
    }

    public Path root() {
        return root;
    }

    public Path databaseFile() {
        return dataDirectory.resolve("attendance.db");
    }

    public Path backupDirectory() {
        return backupDirectory.resolve("app");
    }

    public Path exportDirectory() {
        return exportDirectory;
    }

    public Path logDirectory() {
        return logDirectory;
    }
}
