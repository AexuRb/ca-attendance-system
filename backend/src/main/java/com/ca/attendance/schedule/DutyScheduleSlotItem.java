package com.ca.attendance.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record DutyScheduleSlotItem(
        long id,
        int weekday,
        String weekdayName,
        LocalDate dutyDate,
        LocalTime startTime,
        LocalTime endTime,
        String title,
        String location,
        String note,
        boolean enabled,
        List<AssigneeItem> assignees,
        String createdByName,
        String updatedByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record AssigneeItem(
            long id,
            Long userId,
            String studentNo,
            String name,
            int sortOrder
    ) {
    }
}
