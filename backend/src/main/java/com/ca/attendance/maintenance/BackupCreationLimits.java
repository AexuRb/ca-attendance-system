package com.ca.attendance.maintenance;

record BackupCreationLimits(
        long maxArchiveBytes,
        int maxZipEntries,
        long maxEntryUncompressedBytes,
        long maxTotalUncompressedBytes
) {
    BackupCreationLimits {
        if (maxArchiveBytes < 1
                || maxZipEntries < 1
                || maxEntryUncompressedBytes < 1
                || maxTotalUncompressedBytes < maxEntryUncompressedBytes) {
            throw new IllegalArgumentException("备份创建容量配置不正确");
        }
    }

    static BackupCreationLimits defaults() {
        return new BackupCreationLimits(
                128L * 1024 * 1024,
                16,
                128L * 1024 * 1024,
                256L * 1024 * 1024
        );
    }
}
