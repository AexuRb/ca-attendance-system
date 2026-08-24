package com.ca.attendance.training;

import com.ca.attendance.common.ApiException;

import java.util.List;
import java.util.Locale;

final class TrainingSessionStatus {
    private static final List<String> VALUES = List.of("PLANNED", "COMPLETED", "CANCELED", "ARCHIVED");

    private TrainingSessionStatus() {
    }

    static String parse(String value) {
        if (value == null || value.isBlank()) {
            return "PLANNED";
        }
        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!VALUES.contains(status)) {
            throw ApiException.badRequest("培训状态不合法");
        }
        return status;
    }
}
