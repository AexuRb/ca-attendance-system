package com.ca.attendance.auth;

import com.ca.attendance.common.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptGuardTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-21T08:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void remoteFailuresAreCountedByAccountInsteadOfTunnelAddress() {
        LoginAttemptGuard guard = new LoginAttemptGuard(CLOCK);

        for (int attempt = 0; attempt < LoginAttemptGuard.REMOTE_MAX_FAILURES; attempt++) {
            AuthService.LoginContext context = new AuthService.LoginContext(
                    true,
                    "203.0.113." + attempt,
                    "test-agent"
            );
            guard.requireAllowed("Admin", context);
            guard.recordFailure("Admin", context);
        }

        assertThatThrownBy(() -> guard.requireAllowed(
                " admin ",
                new AuthService.LoginContext(true, "198.51.100.20", "test-agent")
        )).isInstanceOf(ApiException.class).hasMessageContaining("尝试次数过多");
    }

    @Test
    void localLoginHasALooserIndependentFailureLimit() {
        LoginAttemptGuard guard = new LoginAttemptGuard(CLOCK);
        AuthService.LoginContext local = AuthService.LoginContext.local();

        for (int attempt = 0; attempt < LoginAttemptGuard.LOCAL_MAX_FAILURES; attempt++) {
            guard.requireAllowed("admin", local);
            guard.recordFailure("admin", local);
        }

        assertThatThrownBy(() -> guard.requireAllowed("admin", local))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("尝试次数过多");
        assertThatCode(() -> guard.requireAllowed(
                "admin",
                new AuthService.LoginContext(true, "127.0.0.1", "test-agent")
        )).doesNotThrowAnyException();
    }

    @Test
    void successfulLocalLoginClearsRemoteLockForTheSameAccount() {
        LoginAttemptGuard guard = new LoginAttemptGuard(CLOCK);
        AuthService.LoginContext remote = new AuthService.LoginContext(true, "127.0.0.1", "test-agent");

        LoginAttemptGuard.FailureResult lastFailure = null;
        for (int attempt = 0; attempt < LoginAttemptGuard.REMOTE_MAX_FAILURES; attempt++) {
            lastFailure = guard.recordFailure("admin", remote);
        }

        assertThat(lastFailure).isNotNull();
        assertThat(lastFailure.lockedNow()).isTrue();
        assertThatThrownBy(() -> guard.requireAllowed("admin", remote))
                .isInstanceOf(ApiException.class);

        guard.recordSuccess("admin", AuthService.LoginContext.local());

        assertThatCode(() -> guard.requireAllowed("admin", remote)).doesNotThrowAnyException();
    }

    @Test
    void remoteLockExpiresAtTheConfiguredBoundary() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-21T08:00:00Z"));
        LoginAttemptGuard guard = new LoginAttemptGuard(clock);
        AuthService.LoginContext remote = new AuthService.LoginContext(true, "127.0.0.1", "test-agent");

        for (int attempt = 0; attempt < LoginAttemptGuard.REMOTE_MAX_FAILURES; attempt++) {
            guard.recordFailure("admin", remote);
        }
        assertThatThrownBy(() -> guard.requireAllowed("admin", remote))
                .isInstanceOf(ApiException.class);

        clock.advance(Duration.ofMinutes(10));

        assertThatCode(() -> guard.requireAllowed("admin", remote)).doesNotThrowAnyException();
        assertThat(guard.recordFailure("admin", remote).failures()).isEqualTo(1);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
