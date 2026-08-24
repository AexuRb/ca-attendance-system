package com.ca.attendance.attendance;

import com.ca.attendance.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AttendanceRepositoryTest {
    @Test
    void updateReviewRejectsUnknownPartBeforeExecutingSql() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AttendanceRepository repository = new AttendanceRepository(jdbc);

        assertThatThrownBy(() -> repository.updateReview(1L, "UNKNOWN", "APPROVED", 2L, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("CHECK_IN 或 CHECK_OUT");

        verifyNoInteractions(jdbc);
    }
}
