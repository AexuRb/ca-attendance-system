package com.ca.attendance.schedule.domain;

public record ScheduleAssignee(
        Long userId,
        String studentNo,
        String name,
        int sortOrder,
        boolean reassigned,
        String originalName
) {
    public ScheduleAssignee asReplacement(ScheduleAssignee replacement) {
        return new ScheduleAssignee(
                replacement.userId(), replacement.studentNo(), replacement.name(), sortOrder, true, name
        );
    }
}
