package com.ca.attendance.auth;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {
    private final Map<String, AuthUser> tokens = new ConcurrentHashMap<>();
    private final long tokenHours;

    public TokenService(@Value("${app.auth.token-hours:12}") long tokenHours) {
        this.tokenHours = tokenHours;
    }

    public String issue(long id, String studentNo, String name, Role role) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new AuthUser(id, studentNo, name, role, Instant.now().plus(tokenHours, ChronoUnit.HOURS)));
        return token;
    }

    public AuthUser require(String token) {
        AuthUser user = tokens.get(token);
        if (user == null || user.expired()) {
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
}
