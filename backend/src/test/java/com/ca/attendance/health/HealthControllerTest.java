package com.ca.attendance.health;

import com.ca.attendance.access.RemoteAccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {
    @Test
    void remoteHealthResponseContainsNoApplicationOrDatabaseFingerprint() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        HealthController controller = new HealthController(jdbc, new RemoteAccessPolicy(8081));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.setLocalPort(8081);

        assertThat(controller.health(request)).isEqualTo(Map.of("status", "ok"));
    }

    @Test
    void localHealthResponseKeepsDesktopDiagnostics() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        HealthController controller = new HealthController(jdbc, new RemoteAccessPolicy(8081));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.setLocalPort(8080);

        assertThat(controller.health(request))
                .containsEntry("application", "ca-attendance-system")
                .containsEntry("databaseType", "SQLite");
    }
}
