package com.ca.attendance.term.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AcademicTerm(
        long id,
        String code,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        TermStatus status,
        boolean legacy,
        LocalDateTime settlingStartedAt,
        LocalDateTime sealedAt,
        LocalDateTime reopenedAt,
        String reopenReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
