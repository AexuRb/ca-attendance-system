package com.ca.attendance.auth;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {
    private final Map<String, AuthUser> tokens = new ConcurrentHashMap<>();
    private final long tokenHours;
    private final Clock clock;

    public TokenService(@Value("${app.auth.token-hours:12}") long tokenHours) {
        this(tokenHours, Clock.systemUTC());
    }

    @Autowired
    public TokenService(@Value("${app.auth.token-hours:12}") long tokenHours, Clock clock) {
        this.tokenHours = tokenHours;
        this.clock = clock;
    }

    public String issue(long id, String studentNo, String name, Role role) {
        Instant now = clock.instant();
        tokens.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new AuthUser(id, studentNo, name, role, now.plus(tokenHours, ChronoUnit.HOURS)));
        return token;
    }

    public AuthUser require(String token) {
        AuthUser user = tokens.get(token);
        if (user == null || clock.instant().isAfter(user.expiresAt())) {
            if (user != null) {
                tokens.remove(token);
            }
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return user;
    }

    public void revoke(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    public void revokeUser(long userId) {
        tokens.entrySet().removeIf(entry -> entry.getValue().id() == userId);
    }

    public void revokeAll() {
        tokens.clear();
    }

    int activeTokenCount() {
        return tokens.size();
    }
}
