package com.ca.attendance.verification;

import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Savepoint;
import java.sql.SQLException;

/**
 * Opt-in verification against the original database, without a physical commit.
 * Spring's real transaction interceptor still begins/commits/rolls back each service
 * transaction; only its connection boundary is mapped to a SQLite savepoint inside
 * an enclosing transaction. A service rollback therefore remains independently
 * observable, and successful SQL can be inspected before the enclosing rollback.
 * No database is created/copied, no production bean or transaction rule is changed.
 */
final class ExistingDatabaseRollbackDataSource extends SingleConnectionDataSource {
    private final Boundary boundary;

    ExistingDatabaseRollbackDataSource(Path database) throws Exception {
        this(new Boundary(database));
    }

    private ExistingDatabaseRollbackDataSource(Boundary boundary) {
        super(boundary.facade, true);
        this.boundary = boundary;
    }

    void rollbackAll() throws SQLException {
        boundary.physical.rollback();
        boundary.serviceBoundary = null;
        boundary.autoCommit = true;
    }

    @Override public void destroy() {
        try {
            rollbackAll();
        } catch (SQLException ex) {
            throw new IllegalStateException("Verification rollback failed", ex);
        } finally {
            try { boundary.physical.close(); }
            catch (SQLException ex) { throw new IllegalStateException(ex); }
        }
    }

    private static final class Boundary {
        final Connection physical;
        final Connection facade;
        Savepoint serviceBoundary;
        boolean autoCommit = true;

        Boundary(Path database) throws Exception {
            if (!Files.isRegularFile(database)) throw new IllegalStateException("Original database must already exist");
            physical = DriverManager.getConnection("jdbc:sqlite:" + database.toUri().toASCIIString() + "?mode=rw");
            try (var statement = physical.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=10000");
                try (var version = statement.executeQuery("PRAGMA user_version")) {
                    if (!version.next() || version.getInt(1) != 11) {
                        throw new IllegalStateException("Verification requires the existing version 11 schema");
                    }
                }
            }
            physical.setAutoCommit(false);
            facade = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getAutoCommit": return autoCommit;
                            case "setAutoCommit":
                                boolean requested = (boolean) args[0];
                                if (!requested && autoCommit) serviceBoundary = physical.setSavepoint();
                                if (requested && serviceBoundary != null) {
                                    throw new SQLException("Service transaction was not completed");
                                }
                                autoCommit = requested;
                                return null;
                            case "commit":
                                if (serviceBoundary == null) throw new SQLException("No service transaction");
                                physical.releaseSavepoint(serviceBoundary);
                                serviceBoundary = null;
                                return null;
                            case "rollback":
                                if (args == null || args.length == 0) {
                                    if (serviceBoundary == null) throw new SQLException("No service transaction");
                                    physical.rollback(serviceBoundary);
                                    physical.releaseSavepoint(serviceBoundary);
                                    serviceBoundary = null;
                                    return null;
                                }
                                break;
                            case "close": return null;
                            case "unwrap":
                                if (((Class<?>) args[0]).isInstance(proxy)) return proxy;
                                throw new SQLException("Physical connection is protected during verification");
                        }
                        try { return method.invoke(physical, args); }
                        catch (InvocationTargetException ex) { throw ex.getCause(); }
                    });
        }
    }
}
