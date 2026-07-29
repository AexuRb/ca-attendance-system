package com.ca.attendance.attendance;

import com.ca.attendance.common.ApiException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PublicAttendanceRequestGuard {
    private static final int DEFAULT_LOOKUP_LIMIT = 60;
    private static final int DEFAULT_SUBMIT_LIMIT = 30;
    private static final long DEFAULT_WINDOW_MILLIS = 60_000;
    private static final int MAX_CLIENT_KEY_LENGTH = 128;

    private final int lookupLimit;
    private final int submitLimit;
    private final long windowMillis;
    private final Map<String, Counter> counters = new HashMap<>();

    public PublicAttendanceRequestGuard() {
        this(DEFAULT_LOOKUP_LIMIT, DEFAULT_SUBMIT_LIMIT, DEFAULT_WINDOW_MILLIS);
    }

    PublicAttendanceRequestGuard(int lookupLimit, int submitLimit, long windowMillis) {
        this.lookupLimit = lookupLimit;
        this.submitLimit = submitLimit;
        this.windowMillis = windowMillis;
    }

    public synchronized void requireLookup(String clientAddress) {
        require("lookup", clientAddress, lookupLimit);
    }

    public synchronized void requireSubmission(String clientAddress) {
        require("submit", clientAddress, submitLimit);
    }

    private void require(String action, String clientAddress, int limit) {
        long now = System.currentTimeMillis();
        counters.entrySet().removeIf(entry -> now - entry.getValue().windowStartedAt() >= windowMillis);
        String key = action + ":" + normalizeAddress(clientAddress);
        Counter current = counters.get(key);
        if (current == null || now - current.windowStartedAt() >= windowMillis) {
            counters.put(key, new Counter(now, 1));
            return;
        }
        if (current.count() >= limit) {
            throw ApiException.tooManyRequests("操作过于频繁，请稍后再试");
        }
        counters.put(key, new Counter(current.windowStartedAt(), current.count() + 1));
    }

    private String normalizeAddress(String value) {
        String clean = value == null || value.isBlank() ? "unknown" : value.replace("\r", "").replace("\n", "").trim();
        return clean.substring(0, Math.min(clean.length(), MAX_CLIENT_KEY_LENGTH));
    }

    private record Counter(long windowStartedAt, int count) {
    }
}
