package com.ca.attendance.settings;

public record DutyPeriodItem(
        int sortOrder,
        String startTime,
        String endTime
) {
}
