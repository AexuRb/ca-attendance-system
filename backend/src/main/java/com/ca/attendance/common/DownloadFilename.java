package com.ca.attendance.common;

import java.util.Locale;

public final class DownloadFilename {
    public static final int DEFAULT_STEM_MAX_LENGTH = 80;

    private DownloadFilename() {
    }

    public static String xlsx(String requested, String fallback) {
        String value = requested == null ? "" : requested.trim();
        if (value.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            value = value.substring(0, value.length() - 5);
        }
        return stem(value, fallback, DEFAULT_STEM_MAX_LENGTH) + ".xlsx";
    }

    public static String stem(String value, String fallback) {
        return stem(value, fallback, DEFAULT_STEM_MAX_LENGTH);
    }

    public static String stem(String value, String fallback, int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("文件名长度上限必须大于 0");
        }
        String normalized = sanitize(value);
        if (normalized.isBlank()) {
            normalized = sanitize(fallback);
        }
        if (normalized.isBlank()) {
            normalized = "download";
        }
        return normalized.substring(0, Math.min(normalized.length(), maxLength));
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .trim();
        while (normalized.endsWith(".") || normalized.endsWith(" ")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
