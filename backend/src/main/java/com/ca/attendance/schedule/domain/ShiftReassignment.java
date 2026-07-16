package com.ca.attendance.schedule.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ShiftReassignment(
        long id,
        long termId,
        LocalDate date,
        Long sourceSlotId,
        LocalTime startTime,
        LocalTime endTime,
        ScheduleAssignee original,
        ScheduleAssignee replacement,
        String reason,
        String createdByName,
        String updatedByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
