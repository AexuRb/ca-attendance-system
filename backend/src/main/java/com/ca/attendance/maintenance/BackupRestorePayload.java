package com.ca.attendance.maintenance;

import java.nio.file.Path;
import java.util.Map;

record BackupRestorePayload(
        Map<String, Path> tableFiles
) {
}
