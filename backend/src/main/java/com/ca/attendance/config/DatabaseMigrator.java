package com.ca.attendance.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseMigrator implements CommandLineRunner {
    private static final int CURRENT_VERSION = 1;
    private final DataSource dataSource;

    public DatabaseMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            int version = userVersion(connection);
            if (version > CURRENT_VERSION) {
                throw new IllegalStateException("数据库版本高于当前程序支持版本，请使用更新版本的应用");
            }
            if (version < 1) {
                migrateToVersionOne(connection);
            }
            verifyIntegrity(connection);
        }
    }

    private void migrateToVersionOne(Connection connection) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/sqlite/V1__initial_schema.sql"));
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA user_version = 1");
            }
            connection.commit();
        } catch (RuntimeException | SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private int userVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private void verifyIntegrity(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA quick_check")) {
            while (result.next()) {
                String status = result.getString(1);
                if (!"ok".equalsIgnoreCase(status)) {
                    throw new IllegalStateException("SQLite 数据库完整性检查失败：" + status);
                }
            }
        }

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (result.next()) {
                throw new IllegalStateException("SQLite 数据库存在无效的外键引用，请先从备份恢复");
            }
        }
    }
}
