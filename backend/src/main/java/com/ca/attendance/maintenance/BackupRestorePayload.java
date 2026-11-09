package com.ca.attendance.maintenance;

import java.nio.file.Path;
import java.util.Map;

record BackupRestorePayload(
        Map<String, Path> tableFiles,
        boolean clearInvalidContacts,
        int clearedPhoneCount,
        int clearedQqCount
) {
    BackupRestorePayload(Map<String, Path> tableFiles) { this(tableFiles, false, 0, 0); }
}
