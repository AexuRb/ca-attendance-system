package com.ca.attendance.auth;

import com.ca.attendance.common.Role;

import java.time.Instant;

public record AuthUser(
        long id,
        String studentNo,
        String name,
        Role role,
        Instant expiresAt
) {
    public boolean expired() {
        return Instant.now().isAfter(expiresAt);
    }
}
