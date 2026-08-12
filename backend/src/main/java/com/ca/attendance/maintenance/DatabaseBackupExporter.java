package com.ca.attendance.maintenance;

import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class DatabaseBackupExporter implements BackupTableSource {
    private static final List<TableExport> TABLES = List.of(
            new TableExport("users", "SELECT * FROM users ORDER BY id"),
            new TableExport("training_sessions", "SELECT * FROM training_sessions ORDER BY training_date DESC, id DESC"),
            new TableExport("training_participants", "SELECT * FROM training_participants ORDER BY session_id, student_no_snapshot"),
            new TableExport("duty_schedule_slots", "SELECT * FROM duty_schedule_slots ORDER BY weekday, start_time, id"),
            new TableExport("duty_schedule_assignees", "SELECT * FROM duty_schedule_assignees ORDER BY slot_id, sort_order, id"),
            new TableExport("repair_case_sequences", "SELECT * FROM repair_case_sequences ORDER BY sequence_date"),
            new TableExport("repair_cases", "SELECT * FROM repair_cases ORDER BY received_at DESC, id DESC"),
            new TableExport("attendance_records", "SELECT * FROM attendance_records ORDER BY duty_date DESC, check_in_time DESC, id DESC"),
            new TableExport("public_attendance_submissions", "SELECT * FROM public_attendance_submissions ORDER BY created_at, request_id"),
            new TableExport("operation_logs", "SELECT * FROM operation_logs ORDER BY created_at DESC, id DESC"),
            new TableExport("duty_weekday_settings", "SELECT * FROM duty_weekday_settings ORDER BY weekday"),
            new TableExport("app_settings", "SELECT * FROM app_settings ORDER BY setting_key")
    );

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    DatabaseBackupExporter(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public void writeTables(TableWriter writer) throws IOException {
        try {
            transactions.executeWithoutResult(status -> {
                try {
                    for (TableExport table : TABLES) {
                        try (Stream<Map<String, Object>> rows = jdbc.queryForStream(
                                table.sql(),
                                new ColumnMapRowMapper()
                        )) {
                            writer.write(table.name(), rows);
                        }
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    List<String> tableNames() {
        return TABLES.stream().map(TableExport::name).toList();
    }

    private record TableExport(String name, String sql) {
    }
}
