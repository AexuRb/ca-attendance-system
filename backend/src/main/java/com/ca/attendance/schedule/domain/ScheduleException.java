package com.ca.attendance.schedule.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record ScheduleException(
        long id,
        long termId,
        LocalDate date,
        ScheduleAdjustmentType type,
        Long sourceSlotId,
        LocalTime startTime,
        LocalTime endTime,
        String title,
        String location,
        String reason,
        List<ScheduleAssignee> assignees,
        String createdByName,
        String updatedByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
