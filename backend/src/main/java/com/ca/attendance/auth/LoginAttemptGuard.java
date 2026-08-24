package com.ca.attendance.auth;

import com.ca.attendance.common.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class LoginAttemptGuard {
    static final int LOCAL_MAX_FAILURES = 10;
    static final int REMOTE_MAX_FAILURES = 5;

    private static final Policy LOCAL_POLICY = new Policy(
            LOCAL_MAX_FAILURES,
            Duration.ofMinutes(5),
            Duration.ofMinutes(1),
            "本机登录尝试次数过多，请 1 分钟后再试"
    );
    private static final Policy REMOTE_POLICY = new Policy(
            REMOTE_MAX_FAILURES,
            Duration.ofMinutes(15),
            Duration.ofMinutes(10),
            "远程登录尝试次数过多，请 10 分钟后再试"
    );
    private static final int MAX_TRACKED_KEYS = 2048;

    private final Map<String, AttemptState> attempts = new HashMap<>();
    private final Clock clock;

    @Autowired
    public LoginAttemptGuard() {
        this(Clock.systemUTC());
    }

    LoginAttemptGuard(Clock clock) {
        this.clock = clock;
    }

    public synchronized void requireAllowed(String account, AuthService.LoginContext context) {
        String key = key(account, context.remote());
        AttemptState state = attempts.get(key);
        if (state == null) {
            return;
        }

        Instant now = clock.instant();
        if (state.blockedUntil() != null) {
            if (state.blockedUntil().isAfter(now)) {
                throw ApiException.tooManyRequests(policy(context.remote()).blockedMessage());
            }
            attempts.remove(key);
            return;
        }
        if (!state.lastAttempt().plus(state.window()).isAfter(now)) {
            attempts.remove(key);
        }
    }

    public synchronized FailureResult recordFailure(String account, AuthService.LoginContext context) {
        Instant now = clock.instant();
        Policy policy = policy(context.remote());
        String key = key(account, context.remote());
        AttemptState previous = attempts.get(key);
        boolean previousExpired = previous == null || expired(previous, now);
        int failures = previousExpired ? 1 : previous.failures() + 1;
        boolean lockedNow = failures == policy.maxFailures();
        Instant blockedUntil = failures >= policy.maxFailures() ? now.plus(policy.blockDuration()) : null;
        attempts.put(key, new AttemptState(failures, now, blockedUntil, policy.attemptWindow()));
        cleanup(now);
        return new FailureResult(failures, lockedNow, blockedUntil);
    }

    public synchronized void recordSuccess(String account, AuthService.LoginContext context) {
        attempts.remove(key(account, context.remote()));
        if (!context.remote()) {
            attempts.remove(key(account, true));
        }
    }

    private Policy policy(boolean remote) {
        return remote ? REMOTE_POLICY : LOCAL_POLICY;
    }

    private String key(String account, boolean remote) {
        String normalizedAccount = account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
        return (remote ? "REMOTE|" : "LOCAL|") + normalizedAccount;
    }

    private void cleanup(Instant now) {
        if (attempts.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        attempts.entrySet().removeIf(entry -> expired(entry.getValue(), now));
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

    private boolean expired(AttemptState state, Instant now) {
        if (state.blockedUntil() != null) {
            return !state.blockedUntil().isAfter(now);
        }
        return !state.lastAttempt().plus(state.window()).isAfter(now);
    }

    public record FailureResult(int failures, boolean lockedNow, Instant blockedUntil) {
    }

    private record Policy(int maxFailures, Duration attemptWindow, Duration blockDuration, String blockedMessage) {
    }

    private record AttemptState(int failures, Instant lastAttempt, Instant blockedUntil, Duration window) {
    }
}
