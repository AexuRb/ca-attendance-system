package com.ca.attendance.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class SQLiteDataSourceConfiguration {
    @Bean(destroyMethod = "close")
    public DataSource dataSource(StoragePaths storagePaths) {
        SQLiteConfig sqlite = new SQLiteConfig();
        sqlite.enforceForeignKeys(true);
        sqlite.setBusyTimeout(10_000);
        sqlite.setJournalMode(SQLiteConfig.JournalMode.WAL);
        sqlite.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        sqlite.setDateClass("TEXT");
        sqlite.setDateStringFormat("yyyy-MM-dd HH:mm:ss");
        sqlite.setWalAutocheckpoint(1_000);

        SQLiteDataSource delegate = new SQLiteDataSource(sqlite);
        String databasePath = storagePaths.databaseFile().toString().replace('\\', '/');
        delegate.setUrl("jdbc:sqlite:" + databasePath);

        HikariConfig pool = new HikariConfig();
        pool.setPoolName("attendance-sqlite");
        pool.setDataSource(delegate);
        pool.setMaximumPoolSize(1);
        pool.setMinimumIdle(1);
        pool.setConnectionTimeout(10_000);
        pool.setValidationTimeout(3_000);
        pool.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(pool);
    }
}
