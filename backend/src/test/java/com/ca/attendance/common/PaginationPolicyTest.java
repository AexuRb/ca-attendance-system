package com.ca.attendance.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationPolicyTest {
    @Test
    void normalizesInvalidAndOversizedRequests() {
        assertThat(PaginationPolicy.normalize(0, 0))
                .isEqualTo(new PaginationPolicy.PageRequest(1, 20));
        assertThat(PaginationPolicy.normalize(3, 200))
                .isEqualTo(new PaginationPolicy.PageRequest(3, 100));
    }

    @Test
    void resolvesOverflowToLastAvailablePage() {
        assertThat(PaginationPolicy.resolvePage(8, 41, 20)).isEqualTo(3);
        assertThat(PaginationPolicy.resolvePage(8, 0, 20)).isEqualTo(1);
    }
}
