package com.ca.attendance.training;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record TrainingSessionItem(
        long id,
        String title,
        LocalDate trainingDate,
        LocalTime startTime,
        LocalTime endTime,
        String location,
        String speaker,
        String description,
        String status,
        long participantCount,
        BigDecimal totalDurationHours,
        long presentCount,
        long absentCount,
        long leaveCount,
        String createdByName,
        String updatedByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
