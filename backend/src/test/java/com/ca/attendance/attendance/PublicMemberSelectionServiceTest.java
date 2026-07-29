package com.ca.attendance.attendance;

import com.ca.attendance.common.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicMemberSelectionServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-29T08:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void tokenCanOnlyBeBoundToOneSubmissionRequest() {
        PublicMemberSelectionService selections =
                new PublicMemberSelectionService(CLOCK, Duration.ofMinutes(5), 32);
        String token = selections.issue("20230001");

        assertThat(selections.resolve(token)).isEqualTo("20230001");
        assertThat(selections.bindForSubmission(token, "request-001"))
                .isEqualTo("20230001");
        assertThat(selections.bindForSubmission(token, "request-001"))
                .isEqualTo("20230001");
        assertThatThrownBy(() -> selections.bindForSubmission(token, "request-002"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("重新查询");
    }

    @Test
    void invalidSelectionTokenDoesNotRevealWhetherAMemberExists() {
        PublicMemberSelectionService selections =
                new PublicMemberSelectionService(CLOCK, Duration.ofMinutes(5), 32);

        assertThatThrownBy(() -> selections.resolve("sel_missing"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("重新查询");
    }
}
