package com.ca.attendance.schedule.application;

import com.ca.attendance.schedule.DutyScheduleService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FixedScheduleCalendarServiceTest {
    @Test
    void weekUsesSingleBatchedScheduleQuery() {
        DutyScheduleService schedules = mock(DutyScheduleService.class);
        LocalDate date = LocalDate.of(2026, 8, 20);
        when(schedules.week(date)).thenReturn(List.of());

        List<?> result = new FixedScheduleCalendarService(schedules).week(date);

        assertThat(result).hasSize(7);
        verify(schedules).week(date);
        verify(schedules, never()).today(org.mockito.ArgumentMatchers.any());
    }
}
