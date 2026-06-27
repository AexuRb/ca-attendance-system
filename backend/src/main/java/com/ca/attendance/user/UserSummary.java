package com.ca.attendance.user;

import com.ca.attendance.common.Role;

import java.time.LocalDateTime;

public record UserSummary(
        long id,
        String studentNo,
        String name,
        Role role,
        String status,
        String grade,
        boolean mustChangePassword,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
