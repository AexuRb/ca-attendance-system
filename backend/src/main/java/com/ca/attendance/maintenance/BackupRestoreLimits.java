package com.ca.attendance.maintenance;

record BackupRestoreLimits(
        long maxArchiveBytes,
        int maxZipEntries,
        long maxEntryUncompressedBytes,
        long maxTotalUncompressedBytes
) {
    BackupRestoreLimits {
        if (maxArchiveBytes < 1
                || maxZipEntries < 1
                || maxEntryUncompressedBytes < 1
                || maxTotalUncompressedBytes < maxEntryUncompressedBytes) {
            throw new IllegalArgumentException("备份恢复容量配置不正确");
        }
    }

    static BackupRestoreLimits defaults() {
        return new BackupRestoreLimits(
                128L * 1024 * 1024,
                16,
                128L * 1024 * 1024,
                256L * 1024 * 1024
        );
    }
}
