package com.ca.attendance.attendance;

import com.ca.attendance.common.Role;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceRecord(
        long id,
        long userId,
        Role userRole,
        String studentNo,
        String name,
        LocalDate dutyDate,
        int dutyWeekday,
        boolean dutyDay,
        boolean withinDutyPeriod,
        boolean requireDutyDay,
        boolean requireDutyPeriod,
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
