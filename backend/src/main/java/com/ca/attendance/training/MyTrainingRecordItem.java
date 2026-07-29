package com.ca.attendance.training;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record MyTrainingRecordItem(
        long participantId,
        long sessionId,
        String title,
        LocalDate trainingDate,
        LocalTime startTime,
        LocalTime endTime,
        String location,
        String speaker,
        String attendanceStatus,
        BigDecimal durationHours,
        String remark
) {
}
