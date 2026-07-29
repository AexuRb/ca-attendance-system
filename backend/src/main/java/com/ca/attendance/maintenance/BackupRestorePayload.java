package com.ca.attendance.maintenance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record BackupRestorePayload(
        Map<String, List<LinkedHashMap<String, Object>>> rows
) {
}
