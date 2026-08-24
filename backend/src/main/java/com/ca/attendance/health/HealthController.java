package com.ca.attendance.health;

import com.ca.attendance.access.RemoteAccessPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final JdbcTemplate jdbc;
    private final RemoteAccessPolicy remoteAccess;

    public HealthController(JdbcTemplate jdbc, RemoteAccessPolicy remoteAccess) {
        this.jdbc = jdbc;
        this.remoteAccess = remoteAccess;
    }

    @GetMapping
    public Map<String, Object> health(HttpServletRequest request) {
        Integer db = jdbc.queryForObject("SELECT 1", Integer.class);
        if (remoteAccess.isRemote(request)) {
            return Map.of("status", "ok");
        }
        return Map.of(
                "status", "ok",
                "application", "ca-attendance-system",
                "databaseType", "SQLite",
                "database", db == null ? 0 : db
        );
    }
}
