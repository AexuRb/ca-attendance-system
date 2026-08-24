package com.ca.attendance.common;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcTimeTest {

    @Test
    void malformedDateIsReportedAsSqlException() throws SQLException {
        ResultSet result = mock(ResultSet.class);
        when(result.getObject("duty_date")).thenReturn("2026-02-30");

        assertThatThrownBy(() -> JdbcTime.localDate(result, "duty_date"))
                .isInstanceOf(SQLException.class)
                .hasMessage("Invalid date value in duty_date: 2026-02-30")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void malformedDateTimeIsReportedAsSqlException() throws SQLException {
        ResultSet result = mock(ResultSet.class);
        when(result.getObject("created_at")).thenReturn("2026-02-30 10:00:00");

        assertThatThrownBy(() -> JdbcTime.localDateTime(result, "created_at"))
                .isInstanceOf(SQLException.class)
                .hasMessage("Invalid date-time value in created_at: 2026-02-30 10:00:00")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void malformedTimeIsReportedAsSqlException() throws SQLException {
        ResultSet result = mock(ResultSet.class);
        when(result.getObject("start_time")).thenReturn("25:00:00");

        assertThatThrownBy(() -> JdbcTime.localTime(result, "start_time"))
                .isInstanceOf(SQLException.class)
                .hasMessage("Invalid time value in start_time: 25:00:00")
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
