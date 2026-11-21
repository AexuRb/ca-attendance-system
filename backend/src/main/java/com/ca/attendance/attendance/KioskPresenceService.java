package com.ca.attendance.attendance;

import com.ca.attendance.access.RolePermissionPolicy;
import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.schedule.application.FixedScheduleCalendarService;
import com.ca.attendance.schedule.domain.FixedScheduleDay;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KioskPresenceService {
    private final AttendanceRepository attendance;
    private final FixedScheduleCalendarService calendar;
    private final Clock clock;

    public KioskPresenceService(AttendanceRepository attendance, FixedScheduleCalendarService calendar, Clock clock) {
        this.attendance = attendance;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Snapshot current() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<AttendanceRepository.PresentMember> present = attendance.findPresentMembers(now.toLocalDate());
        if (present.isEmpty()) return new Snapshot(0, List.of());

        LocalTime time = now.toLocalTime();
        Set<Long> scheduled = calendar.day(now.toLocalDate()).slots().stream()
                .filter(slot -> slot.startTime() != null && slot.endTime() != null
                        && !time.isBefore(slot.startTime()) && time.isBefore(slot.endTime()))
                .flatMap(slot -> slot.assignees().stream())
                .map(FixedScheduleDay.Assignee::userId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<String> names = present.stream()
                .filter(member -> !scheduled.contains(member.id()))
                .map(AttendanceRepository.PresentMember::name)
                .toList();
        return new Snapshot(present.size(), names);
    }

    public record Snapshot(int total, List<String> names) {}

    @Transactional(readOnly = true)
    public OverviewRoster currentOverview() {
        Role role = AuthContext.current().role();
        if (!RolePermissionPolicy.allows(role, RolePermissionPolicy.Permission.SKILL_ASSESSMENT_CONDUCT)
                && !RolePermissionPolicy.allows(role, RolePermissionPolicy.Permission.MEMBERS_MANAGE)) {
            throw ApiException.forbidden("当前账号无权查看在场人员名单");
        }
        LocalDate date = LocalDate.now(clock);
        return new OverviewRoster(date, attendance.findPresentMembers(date));
    }

    public record OverviewRoster(LocalDate date, List<AttendanceRepository.PresentMember> items) {}
}
