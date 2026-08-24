package com.ca.attendance.maintenance;

record BackupRetentionPolicy(int maxFiles, long maxTotalBytes) {
    static final int DEFAULT_MAX_FILES = 50;
    static final long DEFAULT_MAX_TOTAL_BYTES = 5L * 1024 * 1024 * 1024;

    BackupRetentionPolicy {
        if (maxFiles < 1) {
            throw new IllegalArgumentException("备份保留数量必须大于 0");
        }
        if (maxTotalBytes < 1) {
            throw new IllegalArgumentException("备份保留空间必须大于 0");
        }
    }

    static BackupRetentionPolicy defaults() {
        return new BackupRetentionPolicy(DEFAULT_MAX_FILES, DEFAULT_MAX_TOTAL_BYTES);
    }
}
