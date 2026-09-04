package com.ca.attendance.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigratorStartupTest {
    @TempDir
    Path tempDirectory;

    @Test
    void dataSourceIsFullyMigratedBeforeItIsReturned() {
        SQLiteDataSourceConfiguration configuration = new SQLiteDataSourceConfiguration();
        HikariDataSource dataSource = (HikariDataSource) configuration.dataSource(
                new StoragePaths(tempDirectory.toString())
        );

        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertThat(jdbc.queryForObject("PRAGMA user_version", Integer.class)).isEqualTo(11);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'public_attendance_submissions'",
                    Integer.class
            )).isEqualTo(1);
        } finally {
            dataSource.close();
        }
    }
}
