package com.ca.attendance.repair;

import com.ca.attendance.common.ApiException;

import java.util.Locale;
import java.util.Set;

final class RepairStatus {
    private static final Set<String> LEGACY_IN_PROGRESS = Set.of("RECEIVED", "DIAGNOSING", "WAITING_PICKUP");
    private static final Set<String> STORED_IN_PROGRESS = Set.of(
            "RECEIVED", "DIAGNOSING", "REPAIRING", "WAITING_PICKUP"
    );

    private RepairStatus() {
    }

    static String parse(String value) {
        String status = value == null || value.isBlank()
                ? "REPAIRING"
                : value.trim().toUpperCase(Locale.ROOT);
        if ("IN_PROGRESS".equals(status)
                || "进行中".equals(status)
                || "REPAIRING".equals(status)
                || LEGACY_IN_PROGRESS.contains(status)) {
            return "REPAIRING";
        }
        if ("COMPLETED".equals(status) || "已完成".equals(status)) {
            return "COMPLETED";
        }
        if ("CANCELED".equals(status) || "已取消".equals(status)) {
            return "CANCELED";
        }
        throw ApiException.badRequest("维修状态不合法");
    }

    static String normalizeStored(String status) {
        return STORED_IN_PROGRESS.contains(status) ? "REPAIRING" : status;
    }
}
