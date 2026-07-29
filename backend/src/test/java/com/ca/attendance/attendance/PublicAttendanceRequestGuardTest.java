package com.ca.attendance.attendance;

import com.ca.attendance.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicAttendanceRequestGuardTest {
    @Test
    void limitsRepeatedLookupRequestsFromTheSameClient() {
        PublicAttendanceRequestGuard guard =
                new PublicAttendanceRequestGuard(2, 2, 60_000);

        assertThatCode(() -> {
            guard.requireLookup("127.0.0.1");
            guard.requireLookup("127.0.0.1");
        }).doesNotThrowAnyException();

        assertThatThrownBy(() -> guard.requireLookup("127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("稍后");
        assertThatCode(() -> guard.requireLookup("127.0.0.2"))
                .doesNotThrowAnyException();
    }
}
