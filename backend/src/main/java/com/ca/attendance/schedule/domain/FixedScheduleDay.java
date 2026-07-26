package com.ca.attendance.schedule.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record FixedScheduleDay(
        LocalDate date,
        int weekday,
        String weekdayName,
        List<FixedSlot> slots
) {
    public record FixedSlot(
            String key,
            long id,
            LocalTime startTime,
            LocalTime endTime,
            String title,
            String location,
            String note,
            List<Assignee> assignees
    ) {
    }

    public record Assignee(
            Long userId,
            String studentNo,
            String name,
            int sortOrder
    ) {
    }
}
