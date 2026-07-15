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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private PublicSubmissionRepository submissions;

    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void submitPublicCreatesCheckInAndMarksRecordIncomplete() {
        AttendanceService service = new AttendanceService(users, records, weekdays, periods, logs, backups, submissions);
        UserSummary member = user(1L, "20230001", "张三", Role.MEMBER);

        when(weekdays.isDutyWeekday(anyInt())).thenReturn(true);
        when(periods.contains(any())).thenReturn(true);
        when(users.findActiveByStudentNo("20230001")).thenReturn(Optional.of(member));
        when(records.findOpenToday(eq(1L), any(LocalDate.class))).thenReturn(Optional.empty());
        when(records.insertCheckIn(eq(1L), eq("20230001"), eq("张三"), any(LocalDate.class), anyInt(),
                eq(true), eq(true), any(Timestamp.class), eq("PENDING"), eq("INCOMPLETE"))).thenReturn(10L);
        when(records.findById(10L)).thenReturn(Optional.of(record(10L, null, "PENDING", "NOT_SUBMITTED")));

        when(submissions.findByRequestId("kiosk-check-in-001")).thenReturn(Optional.empty());

        AttendanceService.SubmitResponse response = service.submitPublic("20230001", "kiosk-check-in-001");

        assertThat(response.action()).isEqualTo("CHECK_IN");
        assertThat(response.status()).isEqualTo("PENDING");
        verify(records).updateEffective(10L, 0, 0, "INCOMPLETE");
        verify(submissions).save(any(PublicSubmissionRepository.Receipt.class));
    }

    @Test
    void ministerPublicSubmissionIsAutoApproved() {
        AttendanceService service = new AttendanceService(users, records, weekdays, periods, logs, backups, submissions);
        UserSummary minister = user(2L, "20230002", "测试部长", Role.MINISTER);

        when(weekdays.isDutyWeekday(anyInt())).thenReturn(true);
        when(periods.contains(any())).thenReturn(true);
        when(users.findActiveByStudentNo("20230002")).thenReturn(Optional.of(minister));
        when(records.findOpenToday(eq(2L), any(LocalDate.class))).thenReturn(Optional.empty());
        when(records.insertCheckIn(eq(2L), eq("20230002"), eq("测试部长"), any(LocalDate.class), anyInt(),
                eq(true), eq(true), any(Timestamp.class), eq("AUTO_APPROVED"), eq("INCOMPLETE"))).thenReturn(11L);
        when(records.findById(11L)).thenReturn(Optional.of(record(
                11L, 2L, LocalDateTime.now().minusMinutes(1), null, "AUTO_APPROVED", "NOT_SUBMITTED", true
        )));

        AttendanceService.SubmitResponse response = service.submitPublic("20230002", "minister-check-in-001");

        assertThat(response.status()).isEqualTo("AUTO_APPROVED");
    }

    @Test
    void recomputeApprovedCheckoutRoundsValidHours() {
        AttendanceService service = new AttendanceService(users, records, weekdays, periods, logs, backups, submissions);
        LocalDateTime checkIn = LocalDateTime.of(2026, 6, 30, 8, 0);
        LocalDateTime checkOut = LocalDateTime.of(2026, 6, 30, 10, 34);
        when(records.findById(20L)).thenReturn(Optional.of(record(20L, checkIn, checkOut, "APPROVED", "APPROVED")));

        service.recompute(20L);

        verify(records).updateEffective(20L, 154, 3, "VALID");
    }

    @Test
    void presidentCanDeleteAttendanceRecordWithSafetyBackup() {
        AttendanceService service = new AttendanceService(users, records, weekdays, periods, logs, backups, submissions);
        AuthContext.set(new AuthUser(2L, "president", "会长", Role.PRESIDENT, Instant.now().plusSeconds(3600)));
        AttendanceRecord existing = record(30L, null, "APPROVED", "NOT_SUBMITTED");
        when(records.findById(30L)).thenReturn(Optional.of(existing));
        when(backups.createSystemBackup(anyString())).thenReturn(new BackupService.BackupItem("backup-test.zip", 128L, Instant.now()));

        service.delete(30L, "测试删除");

        verify(backups).createSystemBackup(anyString());
        verify(records).delete(30L);
    }

    @Test
    void ministerCanUpdateAndDeleteMemberRecordFromCurrentWeek() {
        AttendanceService service = new AttendanceService(users, records, weekdays, periods, logs, backups, submissions);
        AuthContext.set(new AuthUser(2L, "minister", "测试部长", Role.MINISTER, Instant.now().plusSeconds(3600)));
        LocalDateTime checkIn = LocalDate.now().atTime(14, 0);
        LocalDateTime checkOut = LocalDate.now().atTime(16, 0);
        AttendanceRecord before = record(31L, 1L, checkIn, checkOut, "APPROVED", "APPROVED", true);
        AttendanceRecord after = record(31L, 1L, checkIn.plusMinutes(5), checkOut, "AUTO_APPROVED", "AUTO_APPROVED", true);
        when(records.findById(31L)).thenReturn(Optional.of(before), Optional.of(after), Optional.of(after));
        when(weekdays.isDutyWeekday(anyInt())).thenReturn(true);
        when(periods.contains(any())).thenReturn(true);
        when(backups.createSystemBackup(anyString())).thenReturn(new BackupService.BackupItem("backup-minister.zip", 128L, Instant.now()));

        AttendanceRecord updated = service.manualUpdate(31L, new AttendanceService.ManualUpdateRequest(
                checkIn.plusMinutes(5), checkOut, "APPROVED", "APPROVED", "修正签到时间"
        ));
        service.delete(31L, "重复签到记录");

        assertThat(updated.checkInTime()).isEqualTo(checkIn.plusMinutes(5));
        verify(records).manualUpdate(eq(31L), any(LocalDate.class), anyInt(), eq(true), eq(true),
                any(Timestamp.class), any(Timestamp.class),
                eq("AUTO_APPROVED"), eq("AUTO_APPROVED"), eq("修正签到时间"), eq(2L));
        verify(backups).createSystemBackup(contains("测试部长"));
        verify(records).delete(31L);
    }

    @Test
    void ministerCannotChangeRecordOutsideCurrentWeekOrOwnedByPresident() {
        AttendanceService service = new AttendanceService(users, records, weekdays, periods, logs, backups, submissions);
        AuthContext.set(new AuthUser(2L, "minister", "测试部长", Role.MINISTER, Instant.now().plusSeconds(3600)));
        LocalDateTime lastWeek = LocalDate.now().minusWeeks(1).atTime(14, 0);
        AttendanceRecord oldMemberRecord = record(32L, 1L, lastWeek, lastWeek.plusHours(2), "APPROVED", "APPROVED", true);
        when(records.findById(32L)).thenReturn(Optional.of(oldMemberRecord));

        assertThatThrownBy(() -> service.delete(32L, "尝试删除历史记录"))
                .hasMessageContaining("本周");

        LocalDateTime thisWeek = LocalDate.now().atTime(14, 0);
        AttendanceRecord presidentRecord = record(33L, 3L, Role.PRESIDENT, thisWeek, thisWeek.plusHours(2), "AUTO_APPROVED", "AUTO_APPROVED", true);
        when(records.findById(33L)).thenReturn(Optional.of(presidentRecord));

        assertThatThrownBy(() -> service.manualUpdate(33L, new AttendanceService.ManualUpdateRequest(
                thisWeek.plusMinutes(5), thisWeek.plusHours(2), "AUTO_APPROVED", "AUTO_APPROVED", "尝试修改会长记录"
        ))).hasMessageContaining("会长或管理员");
    }

    @Test
    void ministerCannotMoveRecordOutsideCurrentWeekOrCreateManualRecord() {
        AttendanceService service = new AttendanceService(users, records, weekdays, periods, logs, backups, submissions);
        AuthContext.set(new AuthUser(2L, "minister", "测试部长", Role.MINISTER, Instant.now().plusSeconds(3600)));
        LocalDateTime thisWeek = LocalDate.now().atTime(14, 0);
        AttendanceRecord memberRecord = record(34L, 1L, thisWeek, thisWeek.plusHours(2), "APPROVED", "APPROVED", true);
        when(records.findById(34L)).thenReturn(Optional.of(memberRecord));

        assertThatThrownBy(() -> service.manualUpdate(34L, new AttendanceService.ManualUpdateRequest(
                LocalDate.now().minusWeeks(1).atTime(14, 0),
                LocalDate.now().minusWeeks(1).atTime(16, 0),
                "APPROVED", "APPROVED", "尝试移动到上周"
        ))).hasMessageContaining("本周");

        assertThatThrownBy(() -> service.manualCreate(new AttendanceService.ManualCreateRequest(
                "20230001", thisWeek, thisWeek.plusHours(2), "部长补录"
        ))).hasMessageContaining("会长或管理员");
    }

    @Test
    void presidentClearingCheckoutResetsCheckoutStatus() {
        AttendanceService service = new AttendanceService(users, records, weekdays, periods, logs, backups, submissions);
        AuthContext.set(new AuthUser(3L, "president", "测试会长", Role.PRESIDENT, Instant.now().plusSeconds(3600)));
        LocalDateTime checkIn = LocalDate.now().atTime(14, 0);
        AttendanceRecord before = record(35L, 1L, checkIn, checkIn.plusHours(2), "APPROVED", "APPROVED", true);
        AttendanceRecord after = record(35L, 1L, checkIn, null, "APPROVED", "NOT_SUBMITTED", true);
        when(records.findById(35L)).thenReturn(Optional.of(before), Optional.of(after));
        when(weekdays.isDutyWeekday(anyInt())).thenReturn(true);
        when(periods.contains(any())).thenReturn(true);

        service.manualUpdate(35L, new AttendanceService.ManualUpdateRequest(
                checkIn, null, "APPROVED", "APPROVED", "清除错误签退"
        ));

        verify(records).manualUpdate(eq(35L), any(LocalDate.class), anyInt(), eq(true), eq(true),
                any(Timestamp.class), isNull(), eq("APPROVED"), eq("NOT_SUBMITTED"), eq("清除错误签退"), eq(3L));
    }

    @Test
    void submitOutsideConfiguredPeriodKeepsRecordButMarksItIneligible() {
        AttendanceService service = new AttendanceService(users, records, weekdays, periods, logs, backups, submissions);
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

    @Test
    void repeatedPublicSubmissionReturnsTheOriginalReceiptWithoutTogglingAttendance() {
        AttendanceService service = new AttendanceService(users, records, weekdays, periods, logs, backups, submissions);
        LocalDateTime submittedAt = LocalDateTime.of(2026, 7, 11, 14, 30);
        when(submissions.findByRequestId("kiosk-retry-001")).thenReturn(Optional.of(
                new PublicSubmissionRepository.Receipt(
                        "kiosk-retry-001",
                        "20230001",
                        88L,
                        "CHECK_IN",
                        "张三",
                        submittedAt,
                        "PENDING",
                        "签到已提交，等待审核"
                )
        ));

        AttendanceService.SubmitResponse response = service.submitPublic("20230001", "kiosk-retry-001");

        assertThat(response.recordId()).isEqualTo(88L);
        assertThat(response.action()).isEqualTo("CHECK_IN");
        assertThat(response.submittedAt()).isEqualTo(submittedAt);
        verifyNoInteractions(users, records, weekdays, periods);
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
        return record(id, 1L, checkInTime, checkOutTime, checkInStatus, checkOutStatus, withinDutyPeriod);
    }

    private AttendanceRecord record(long id, long userId, LocalDateTime checkInTime, LocalDateTime checkOutTime,
                                    String checkInStatus, String checkOutStatus, boolean withinDutyPeriod) {
        return record(id, userId, Role.MEMBER, checkInTime, checkOutTime, checkInStatus, checkOutStatus, withinDutyPeriod);
    }

    private AttendanceRecord record(long id, long userId, Role userRole, LocalDateTime checkInTime, LocalDateTime checkOutTime,
                                    String checkInStatus, String checkOutStatus, boolean withinDutyPeriod) {
        return new AttendanceRecord(
                id,
                userId,
                userRole,
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
