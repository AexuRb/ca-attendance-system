package com.ca.attendance.repair;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class RepairDisplayText {
    private static final DateTimeFormatter HUMAN_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private RepairDisplayText() {
    }

    static String agreementType(String type) {
        return switch (type) {
            case "PUBLIC_DEVICE" -> "免责协议";
            case "PERSONAL_DEVICE" -> "维修协议";
            default -> type;
        };
    }

    static String status(String status) {
        return switch (status) {
            case "RECEIVED", "DIAGNOSING", "REPAIRING", "WAITING_PICKUP" -> "进行中";
            case "COMPLETED" -> "已完成";
            case "CANCELED" -> "已取消";
            default -> status;
        };
    }

    static String time(LocalDateTime value) {
        return value == null ? "-" : value.format(HUMAN_TIME);
    }

    static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
