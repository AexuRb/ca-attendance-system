package com.ca.attendance.auth;

import com.ca.attendance.common.Role;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenServiceClockTest {
    @Test
    void issuingTokenRemovesExpiredEntriesUsingInjectedClock() {
        Instant firstInstant = Instant.parse("2026-08-24T00:00:00Z");
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(firstInstant, firstInstant.plusSeconds(13 * 60 * 60));
        TokenService tokens = new TokenService(12, clock);

        tokens.issue(1, "first", "第一位", Role.ADMIN);
        tokens.issue(2, "second", "第二位", Role.PRESIDENT);

        assertThat(tokens.activeTokenCount()).isEqualTo(1);
    }
}
