package com.ca.attendance.attendance;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.maintenance.BackupService;
import com.ca.attendance.settings.DutyWeekdayService;
import com.ca.attendance.settings.DutyPeriodService;
import com.ca.attendance.user.UserRepository;
import com.ca.attendance.user.UserSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {
    @Mock
    private UserRepository users;
    @Mock
    private AttendanceRepository records;
    @Mock
    private DutyWeekdayService weekdays;
    @Mock
    private DutyPeriodService periods;
    @Mock
    private OperationLogService logs;
    @Mock
    private BackupService backups;

    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void submitPublicCreatesCheckInAndMarksRecordIncomplete() {
        AttendanceService service = new AttendanceService(users, records, weekdays, periods, logs, backups);
        UserSummary member = user(1L, "20230001", "张三", Role.MEMBER);

        when(weekdays.isDutyWeekday(anyInt())).thenReturn(true);
        when(periods.contains(any())).thenReturn(true);
        when(users.findActiveByStudentNo("20230001")).thenReturn(Optional.of(member));
        when(records.findOpenToday(eq(1L), any(LocalDate.class))).thenReturn(Optional.empty());
        when(records.insertCheckIn(eq(1L), eq("20230001"), eq("张三"), any(LocalDate.class), anyInt(),
                eq(true), eq(true), any(Timestamp.class), eq("PENDING"), eq("INCOMPLETE"))).thenReturn(10L);
        when(records.findById(10L)).thenReturn(Optional.of(record(10L, null, "PENDING", "NOT_SUBMITTED")));

        AttendanceService.SubmitResponse response = service.submitPublic("20230001");

        assertThat(response.action()).isEqualTo("CHECK_IN");
        assertThat(response.status()).isEqualTo("PENDING");
        verify(records).updateEffective(10L, 0, 0, "INCOMPLETE");
    }

    @Test
    void recomputeApprovedCheckoutRoundsValidHours() {
        AttendanceService service = new AttendanceService(users, records, weekdays, periods, logs, backups);
        LocalDateTime checkIn = LocalDateTime.of(2026, 6, 30, 8, 0);
        LocalDateTime checkOut = LocalDateTime.of(2026, 6, 30, 10, 34);
        when(records.findById(20L)).thenReturn(Optional.of(record(20L, checkIn, checkOut, "APPROVED", "APPROVED")));

        service.recompute(20L);

        verify(records).updateEffective(20L, 154, 3, "VALID");
    }

    @Test
    void presidentCanDeleteAttendanceRecordWithSafetyBackup() {
        AttendanceService service = new AttendanceService(users, records, weekdays, periods, logs, backups);
        AuthContext.set(new AuthUser(2L, "president", "会长", Role.PRESIDENT, Instant.now().plusSeconds(3600)));
        AttendanceRecord existing = record(30L, null, "APPROVED", "NOT_SUBMITTED");
        when(records.findById(30L)).thenReturn(Optional.of(existing));
        when(backups.create()).thenReturn(new BackupService.BackupItem("backup-test.zip", 128L, Instant.now()));

        service.delete(30L);

        verify(backups).create();
        verify(records).delete(30L);
    }

    @Test
    void submitOutsideConfiguredPeriodKeepsRecordButMarksItIneligible() {
        AttendanceService service = new AttendanceService(users, records, weekdays, periods, logs, backups);
        UserSummary member = user(1L, "20230001", "张三", Role.MEMBER);
        when(weekdays.isDutyWeekday(anyInt())).thenReturn(true);
        when(periods.contains(any())).thenReturn(false);
        when(users.findActiveByStudentNo("20230001")).thenReturn(Optional.of(member));
        when(records.findOpenToday(eq(1L), any(LocalDate.class))).thenReturn(Optional.empty());
        when(records.insertCheckIn(eq(1L), eq("20230001"), eq("张三"), any(LocalDate.class), anyInt(),
                eq(true), eq(false), any(Timestamp.class), eq("PENDING"), eq("INCOMPLETE"))).thenReturn(40L);
        when(records.findById(40L)).thenReturn(Optional.of(
                record(40L, LocalDateTime.now().minusMinutes(1), null, "PENDING", "NOT_SUBMITTED", false)
        ));

        AttendanceService.SubmitResponse response = service.submitPublic("20230001");

        assertThat(response.message()).contains("不在值班时段");
        verify(records).updateEffective(40L, 0, 0, "INVALID");
    }

    private UserSummary user(long id, String studentNo, String name, Role role) {
        LocalDateTime now = LocalDateTime.now();
        return new UserSummary(id, studentNo, name, role, "ACTIVE", null, null, null, null, false, now, now);
    }

    private AttendanceRecord record(long id, LocalDateTime checkOutTime, String checkInStatus, String checkOutStatus) {
        return record(id, LocalDateTime.now().minusHours(1), checkOutTime, checkInStatus, checkOutStatus, true);
    }

    private AttendanceRecord record(long id, LocalDateTime checkInTime, LocalDateTime checkOutTime,
                                    String checkInStatus, String checkOutStatus) {
        return record(id, checkInTime, checkOutTime, checkInStatus, checkOutStatus, true);
    }

    private AttendanceRecord record(long id, LocalDateTime checkInTime, LocalDateTime checkOutTime,
                                    String checkInStatus, String checkOutStatus, boolean withinDutyPeriod) {
        return new AttendanceRecord(
                id,
                1L,
                "20230001",
                "张三",
                checkInTime.toLocalDate(),
                checkInTime.getDayOfWeek().getValue(),
                true,
                withinDutyPeriod,
                checkInTime,
                checkOutTime,
                checkInStatus,
                checkOutStatus,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                "PENDING",
                "PUBLIC",
                null
        );
    }
}
