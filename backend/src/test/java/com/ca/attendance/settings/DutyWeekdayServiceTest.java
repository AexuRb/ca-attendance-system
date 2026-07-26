package com.ca.attendance.settings;

import com.ca.attendance.log.OperationLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DutyWeekdayServiceTest {
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private OperationLogService logs;

    @Test
    void listConvertsSqliteIntegerFlagsToBooleans() {
        when(jdbc.queryForList("SELECT weekday, weekday_name, enabled FROM duty_weekday_settings ORDER BY weekday"))
                .thenReturn(List.of(
                        Map.of("weekday", 1, "weekday_name", "星期一", "enabled", 0),
                        Map.of("weekday", 2, "weekday_name", "星期二", "enabled", 1)
                ));

        List<Map<String, Object>> result = new DutyWeekdayService(jdbc, logs).list();

        assertThat(result).extracting(row -> row.get("enabled")).containsExactly(false, true);
    }
}
