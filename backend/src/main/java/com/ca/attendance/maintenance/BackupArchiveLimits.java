package com.ca.attendance.maintenance;

final class BackupArchiveLimits {
    static final long MAX_ARCHIVE_BYTES = 128L * 1024 * 1024;
    static final int MAX_ZIP_ENTRIES = 16;
    static final long MAX_ENTRY_UNCOMPRESSED_BYTES = 128L * 1024 * 1024;
    static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 256L * 1024 * 1024;
    static final int MAX_ROWS_PER_TABLE = 100_000;
    static final int MAX_TOTAL_ROWS = 250_000;

    private BackupArchiveLimits() {
    }
}
