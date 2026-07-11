package com.ca.attendance.attendance;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceRecord(
        long id,
        long userId,
        String studentNo,
        String name,
        LocalDate dutyDate,
        int dutyWeekday,
        boolean dutyDay,
        boolean withinDutyPeriod,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        String checkInStatus,
        String checkOutStatus,
        Long checkInReviewedBy,
        Long checkOutReviewedBy,
        LocalDateTime checkInReviewedAt,
        LocalDateTime checkOutReviewedAt,
        String checkInRejectReason,
        String checkOutRejectReason,
        int durationMinutes,
        int validHours,
        String effectiveStatus,
        String source,
        String manualReason
) {
}
