package com.ca.attendance.shared.application;

public interface AuditLogPort {
    void write(String action, String targetType, Long targetId, Object before, Object after, String reason);
}
