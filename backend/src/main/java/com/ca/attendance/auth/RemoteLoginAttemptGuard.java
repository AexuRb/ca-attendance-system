package com.ca.attendance.auth;

import com.ca.attendance.common.ApiException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class RemoteLoginAttemptGuard {
    private static final int MAX_FAILURES = 5;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(10);
    private static final int MAX_TRACKED_KEYS = 2048;
    private final Map<String, AttemptState> attempts = new HashMap<>();

    public synchronized void requireAllowed(String account, AuthService.LoginContext context) {
        AttemptState state = attempts.get(key(account, context));
        Instant now = Instant.now();
        if (state != null && state.blockedUntil() != null && state.blockedUntil().isAfter(now)) {
            throw ApiException.tooManyRequests("远程登录尝试次数过多，请 10 分钟后再试");
        }
        if (state != null && state.lastAttempt().plus(ATTEMPT_WINDOW).isBefore(now)) {
            attempts.remove(key(account, context));
        }
    }

    public synchronized void recordFailure(String account, AuthService.LoginContext context) {
        Instant now = Instant.now();
        String key = key(account, context);
        AttemptState previous = attempts.get(key);
        int failures = previous == null || previous.lastAttempt().plus(ATTEMPT_WINDOW).isBefore(now)
                ? 1
                : previous.failures() + 1;
        Instant blockedUntil = failures >= MAX_FAILURES ? now.plus(BLOCK_DURATION) : null;
        attempts.put(key, new AttemptState(failures, now, blockedUntil));
        cleanup(now);
    }

    public synchronized void recordSuccess(String account, AuthService.LoginContext context) {
        attempts.remove(key(account, context));
    }

    private String key(String account, AuthService.LoginContext context) {
        String normalizedAccount = account == null ? "" : account.trim().toLowerCase();
        return context.clientAddress() + "|" + normalizedAccount;
    }

    private void cleanup(Instant now) {
        if (attempts.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        attempts.entrySet().removeIf(entry -> entry.getValue().lastAttempt().plus(ATTEMPT_WINDOW).isBefore(now));
        while (attempts.size() > MAX_TRACKED_KEYS) {
            String oldest = attempts.entrySet().stream()
                    .min(Map.Entry.comparingByValue((left, right) -> left.lastAttempt().compareTo(right.lastAttempt())))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (oldest == null) {
                return;
            }
            attempts.remove(oldest);
        }
    }

    private record AttemptState(int failures, Instant lastAttempt, Instant blockedUntil) {
    }
}
