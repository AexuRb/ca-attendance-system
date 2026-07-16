package com.ca.attendance.log;

import com.ca.attendance.shared.application.AuditLogPort;
import org.springframework.stereotype.Component;

@Component
public class OperationAuditAdapter implements AuditLogPort {
    private final OperationLogService logs;

    public OperationAuditAdapter(OperationLogService logs) {
        this.logs = logs;
    }

    @Override
    public void write(String action, String targetType, Long targetId,
                      Object before, Object after, String reason) {
        logs.log(action, targetType, targetId, before, after, reason);
    }
}
