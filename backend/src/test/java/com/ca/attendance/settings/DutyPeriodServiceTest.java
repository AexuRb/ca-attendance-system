package com.ca.attendance.settings;

import com.ca.attendance.log.OperationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DutyPeriodServiceTest {
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private OperationLogService logs;

    @Test
    void listReadsStoredPeriodsWithSortOrder() {
        DutyPeriodService service = new DutyPeriodService(jdbc, new ObjectMapper(), logs);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("DUTY_TIME_PERIODS")))
                .thenReturn(List.of("""
                        [{"sortOrder":0,"startTime":"15:00","endTime":"17:00"}]
                        """));

        List<DutyPeriodItem> periods = service.list();

        assertThat(periods).containsExactly(new DutyPeriodItem(0, "15:00", "17:00"));
    }

    @Test
    void containsUsesInclusiveStartAndExclusiveEnd() {
        DutyPeriodService service = new DutyPeriodService(jdbc, new ObjectMapper(), logs);
        when(jdbc.queryForList(anyString(), eq(String.class), eq("DUTY_TIME_PERIODS")))
                .thenReturn(List.of("""
                        [{"sortOrder":0,"startTime":"14:00","endTime":"18:00"}]
                        """));

        assertThat(service.contains(LocalTime.of(14, 0))).isTrue();
        assertThat(service.contains(LocalTime.of(17, 59, 59))).isTrue();
        assertThat(service.contains(LocalTime.of(18, 0))).isFalse();
    }
}
