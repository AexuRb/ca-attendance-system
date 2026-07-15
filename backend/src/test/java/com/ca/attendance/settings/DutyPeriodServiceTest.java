package com.ca.attendance.settings;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.time.LocalTime;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DutyPeriodServiceTest {
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private OperationLogService logs;

    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

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

    @Test
    void ministerCannotUpdateDutyPeriods() {
        DutyPeriodService service = new DutyPeriodService(jdbc, new ObjectMapper(), logs);
        AuthContext.set(new AuthUser(2L, "minister", "测试部长", Role.MINISTER, Instant.now().plusSeconds(3600)));

        assertThatThrownBy(() -> service.update(List.of(
                new DutyPeriodService.DutyPeriodRequest("14:00", "16:00")
        ))).hasMessageContaining("无权调整值班时间段");
    }
}
