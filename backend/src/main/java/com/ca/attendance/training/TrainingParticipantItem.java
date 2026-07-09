package com.ca.attendance.training;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TrainingParticipantItem(
        long id,
        long sessionId,
        Long userId,
        String studentNo,
        String name,
        String attendanceStatus,
        BigDecimal durationHours,
        String remark,
        String source,
        String createdByName,
        String updatedByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
