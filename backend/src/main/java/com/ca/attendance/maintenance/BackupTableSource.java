package com.ca.attendance.maintenance;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

interface BackupTableSource {
    void writeTables(TableWriter writer) throws IOException;

    @FunctionalInterface
    interface TableWriter {
        void write(String table, Stream<Map<String, Object>> rows) throws IOException;
    }
}
