package com.ca.attendance.schedule.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record EffectiveScheduleDay(
        long termId,
        String termName,
        LocalDate date,
        int weekday,
        String weekdayName,
        boolean cancelled,
        List<EffectiveSlot> slots
) {
    public record EffectiveSlot(
            String key,
            Long sourceSlotId,
            Long exceptionId,
            LocalTime startTime,
            LocalTime endTime,
            String title,
            String location,
            String note,
            String origin,
            List<ScheduleAssignee> assignees
    ) {
        public EffectiveSlot withAssignees(List<ScheduleAssignee> values) {
            return new EffectiveSlot(
                    key, sourceSlotId, exceptionId, startTime, endTime,
                    title, location, note, origin, List.copyOf(values)
            );
        }
    }
}
