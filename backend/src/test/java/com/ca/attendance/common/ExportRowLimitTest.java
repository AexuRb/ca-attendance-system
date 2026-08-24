package com.ca.attendance.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportRowLimitTest {
    @Test
    void rejectsOnlyRowsBeyondTheDocumentedLimit() {
        ExportRowLimit.requireWithinLimit(ExportRowLimit.MAX_ROWS);

        assertThatThrownBy(() -> ExportRowLimit.requireWithinLimit(ExportRowLimit.FETCH_LIMIT))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("50000");
    }
}
