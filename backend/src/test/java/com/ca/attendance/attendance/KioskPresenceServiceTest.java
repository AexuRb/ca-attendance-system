package com.ca.attendance.attendance;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.Role;
import com.ca.attendance.schedule.application.FixedScheduleCalendarService;
import com.ca.attendance.schedule.domain.FixedScheduleDay;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static com.ca.attendance.user.AssessmentStatus.PASSED;
import static com.ca.attendance.user.AssessmentStatus.NOT_PASSED;

class KioskPresenceServiceTest {
    private final AttendanceRepository attendance = mock(AttendanceRepository.class);
    private final FixedScheduleCalendarService calendar = mock(FixedScheduleCalendarService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final LocalDate date = LocalDate.of(2026, 11, 21);
    private final KioskPresenceService service = new KioskPresenceService(attendance, calendar, clock);

    @Test
    void overviewRetainsPresentAssigneesWhileKioskShowsThemOnlyInTheSchedule() {
        var signedInMinister = new AttendanceRepository.PresentMember(1, "当班部长示例", PASSED, PASSED);
        var members = List.of(
                signedInMinister,
                new AttendanceRepository.PresentMember(2, "成员示例", PASSED, NOT_PASSED),
                new AttendanceRepository.PresentMember(3, "前一时段示例", PASSED, PASSED),
                new AttendanceRepository.PresentMember(4, "下一时段示例", PASSED, PASSED),
                new AttendanceRepository.PresentMember(5, "重叠时段示例", PASSED, PASSED)
        );
        when(attendance.findPresentMembers(date)).thenReturn(members);
        when(calendar.day(date)).thenReturn(new FixedScheduleDay(date, 6, "星期六", List.of(
                slot(1, 9, 10, 3), slot(2, 10, 11, 1, 6), slot(3, 9, 11, 5), slot(4, 11, 12, 4)
        )));

        KioskPresenceService.Snapshot snapshot = service.current();

        assertThat(snapshot.total()).isEqualTo(5);
        assertThat(snapshot.names()).containsExactly("成员示例", "前一时段示例", "下一时段示例");

        AuthContext.set(new AuthUser(7, "preview-account", "示例管理员", Role.ADMIN, Instant.MAX));
        try {
            var overview = service.currentOverview();
            assertThat(overview.date()).isEqualTo(date);
            assertThat(overview.items()).hasSize(snapshot.total()).containsExactlyElementsOf(members);
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void returnsAnEmptyRosterWithoutReadingSchedule() {
        when(attendance.findPresentMembers(date)).thenReturn(List.of());

        assertThat(service.current()).isEqualTo(new KioskPresenceService.Snapshot(0, List.of()));
        verifyNoInteractions(calendar);
    }

    private FixedScheduleDay.FixedSlot slot(int id, int start, int end, long... users) {
        List<FixedScheduleDay.Assignee> assignees = java.util.Arrays.stream(users)
                .mapToObj(user -> new FixedScheduleDay.Assignee(user, "", "排班示例", 0))
                .toList();
        return new FixedScheduleDay.FixedSlot("slot-" + id, id, LocalTime.of(start, 0), LocalTime.of(end, 0),
                "", "", "", assignees);
    }
}
