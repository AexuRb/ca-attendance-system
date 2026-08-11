package com.ca.attendance.attendance;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.Role;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.maintenance.BackupService;
import com.ca.attendance.settings.DutyWeekdayService;
import com.ca.attendance.settings.DutyPeriodService;
import com.ca.attendance.settings.AttendancePolicyService;
import com.ca.attendance.user.UserRepository;
import com.ca.attendance.user.UserSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
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
    @Mock
    private PublicMemberSelectionService selections;
    @Mock
    private AttendancePolicyService policies;

    @BeforeEach
    void defaultAttendancePolicy() {
        lenient().when(policies.current()).thenReturn(
                new AttendancePolicyService.AttendancePolicy(false, false)
        );
    }

    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    void submitPublicCreatesCheckInAndMarksRecordIncomplete() {
        AttendanceService service = service();
        UserSummary member = user(1L, "20230001", "张三", Role.MEMBER);

        when(weekdays.isDutyWeekday(anyInt())).thenReturn(true);
        when(periods.contains(any())).thenReturn(true);
        when(users.findActiveByStudentNo("20230001")).thenReturn(Optional.of(member));
        when(records.findOpenToday(eq(1L), any(LocalDate.class))).thenReturn(Optional.empty());
        when(records.insertCheckIn(eq(1L), eq("20230001"), eq("张三"), any(LocalDate.class), anyInt(),
                eq(true), eq(true), eq(false), eq(false), any(Timestamp.class),
                eq("PENDING"), eq("INCOMPLETE"))).thenReturn(10L);
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
        AttendanceService service = service();
        UserSummary minister = user(2L, "20230002", "测试部长", Role.MINISTER);

        when(weekdays.isDutyWeekday(anyInt())).thenReturn(true);
        when(periods.contains(any())).thenReturn(true);
        when(users.findActiveByStudentNo("20230002")).thenReturn(Optional.of(minister));
        when(records.findOpenToday(eq(2L), any(LocalDate.class))).thenReturn(Optional.empty());
        when(records.insertCheckIn(eq(2L), eq("20230002"), eq("测试部长"), any(LocalDate.class), anyInt(),
                eq(true), eq(true), eq(false), eq(false), any(Timestamp.class),
                eq("AUTO_APPROVED"), eq("INCOMPLETE"))).thenReturn(11L);
        when(records.findById(11L)).thenReturn(Optional.of(record(
                11L, 2L, LocalDateTime.now().minusMinutes(1), null, "AUTO_APPROVED", "NOT_SUBMITTED", true
        )));

        AttendanceService.SubmitResponse response = service.submitPublic("20230002", "minister-check-in-001");

        assertThat(response.status()).isEqualTo("AUTO_APPROVED");
    }

    @Test
    void recomputeApprovedCheckoutRoundsValidHours() {
        AttendanceService service = service();
        LocalDateTime checkIn = LocalDateTime.of(2026, 6, 30, 8, 0);
        LocalDateTime checkOut = LocalDateTime.of(2026, 6, 30, 10, 34);
        when(records.findById(20L)).thenReturn(Optional.of(record(20L, checkIn, checkOut, "APPROVED", "APPROVED")));

        service.recompute(20L);

        verify(records).updateEffective(20L, 154, 3, "VALID");
    }

    @ParameterizedTest
    @CsvSource({
            "29, 0",
            "30, 1",
            "89, 1",
            "90, 2"
    })
    void recomputeUsesTheDocumentedRoundingBoundaries(int minutes, int expectedHours) {
        AttendanceService service = service();
        long recordId = 100L + minutes;
        LocalDateTime checkIn = LocalDateTime.of(2026, 8, 10, 14, 0);
        when(records.findById(recordId)).thenReturn(Optional.of(
                record(recordId, checkIn, checkIn.plusMinutes(minutes), "APPROVED", "APPROVED")
        ));

        service.recompute(recordId);

        verify(records).updateEffective(recordId, minutes, expectedHours, "VALID");
    }

    @Test
    void recomputeAcceptsAChronologicallyLaterSubsecondCheckout() {
        AttendanceService service = service();
        LocalDateTime checkIn = LocalDateTime.of(2026, 8, 10, 14, 0, 0, 100_000_000);
        LocalDateTime checkOut = checkIn.plusNanos(250_000_000);
        when(records.findById(21L)).thenReturn(Optional.of(
                record(21L, checkIn, checkOut, "APPROVED", "APPROVED")
        ));

        service.recompute(21L);

        verify(records).updateEffective(21L, 0, 0, "VALID");
    }

    @Test
    void presidentCanDeleteAttendanceRecordWithSafetyBackup() {
        AttendanceService service = service();
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
        AttendanceService service = service();
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
        verify(records).manualUpdate(eq(31L), any(LocalDate.class), anyInt(), eq(true), eq(true), eq(false), eq(false),
                any(Timestamp.class), any(Timestamp.class),
                eq("AUTO_APPROVED"), eq("AUTO_APPROVED"), eq("修正签到时间"), eq(2L));
        verify(backups).createSystemBackup(contains("测试部长"));
        verify(records).delete(31L);
    }

    @Test
    void ministerCannotChangeRecordOutsideCurrentWeekOrOwnedByPresident() {
        AttendanceService service = service();
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
        AttendanceService service = service();
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
    void manualCandidatesRequireLeaderPermissionAndReturnActiveAccounts() {
        AttendanceService service = service();
        List<UserRepository.UserCandidate> candidates = List.of(
                new UserRepository.UserCandidate(1L, "20230001", "张三", Role.MEMBER)
        );
        when(users.searchActiveCandidates("张", 1000)).thenReturn(candidates);

        AuthContext.set(new AuthUser(3L, "president", "测试会长", Role.PRESIDENT,
                Instant.now().plusSeconds(3600)));
        assertThat(service.manualCandidates(" 张 ")).isEqualTo(candidates);

        AuthContext.set(new AuthUser(2L, "minister", "测试部长", Role.MINISTER,
                Instant.now().plusSeconds(3600)));
        assertThatThrownBy(() -> service.manualCandidates("张"))
                .hasMessageContaining("会长或管理员");
    }

    @Test
    void presidentClearingCheckoutResetsCheckoutStatus() {
        AttendanceService service = service();
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

        verify(records).manualUpdate(eq(35L), any(LocalDate.class), anyInt(), eq(true), eq(true), eq(false), eq(false),
                any(Timestamp.class), isNull(), eq("APPROVED"), eq("NOT_SUBMITTED"), eq("清除错误签退"), eq(3L));
    }

    @Test
    void ministerCannotReviewOwnPendingRecord() {
        AttendanceService service = service();
        AuthContext.set(new AuthUser(2L, "minister", "测试部长", Role.MINISTER, Instant.now().plusSeconds(3600)));
        AttendanceRecord ownRecord = record(
                37L,
                2L,
                Role.MINISTER,
                LocalDateTime.now().minusHours(1),
                null,
                "PENDING",
                "NOT_SUBMITTED",
                true
        );
        when(records.findById(37L)).thenReturn(Optional.of(ownRecord));

        assertThatThrownBy(() -> service.review(37L, "CHECK_IN", "APPROVE", ""))
                .hasMessageContaining("不能审核自己的记录");

        verify(records, never()).updateReview(anyLong(), anyString(), anyString(), anyLong(), any());
    }

    @Test
    void checkoutTimeCannotBeSavedAsNotSubmitted() {
        AttendanceService service = service();
        AuthContext.set(new AuthUser(3L, "president", "测试会长", Role.PRESIDENT, Instant.now().plusSeconds(3600)));
        LocalDateTime checkIn = LocalDate.now().atTime(14, 0);
        LocalDateTime checkOut = checkIn.plusHours(2);
        when(records.findById(36L)).thenReturn(Optional.of(
                record(36L, 1L, checkIn, null, "APPROVED", "NOT_SUBMITTED", true)
        ));

        assertThatThrownBy(() -> service.manualUpdate(36L, new AttendanceService.ManualUpdateRequest(
                checkIn,
                checkOut,
                "APPROVED",
                "NOT_SUBMITTED",
                "补充签退时间"
        ))).hasMessageContaining("已有签退时间");

        verify(records, never()).manualUpdate(anyLong(), any(), anyInt(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(),
                any(), any(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void submitOutsideConfiguredPeriodKeepsRecordForReview() {
        AttendanceService service = service();
        UserSummary member = user(1L, "20230001", "张三", Role.MEMBER);
        when(weekdays.isDutyWeekday(anyInt())).thenReturn(true);
        when(periods.contains(any())).thenReturn(false);
        when(users.findActiveByStudentNo("20230001")).thenReturn(Optional.of(member));
        when(records.findOpenToday(eq(1L), any(LocalDate.class))).thenReturn(Optional.empty());
        when(records.insertCheckIn(eq(1L), eq("20230001"), eq("张三"), any(LocalDate.class), anyInt(),
                eq(true), eq(false), eq(false), eq(false), any(Timestamp.class),
                eq("PENDING"), eq("INCOMPLETE"))).thenReturn(40L);
        when(records.findById(40L)).thenReturn(Optional.of(
                record(40L, LocalDateTime.now().minusMinutes(1), null, "PENDING", "NOT_SUBMITTED", false)
        ));

        AttendanceService.SubmitResponse response = service.submitPublic("20230001");

        assertThat(response.message()).contains("不在值班时段");
        verify(records).updateEffective(40L, 0, 0, "INCOMPLETE");
    }

    @Test
    void approvedRecordOutsideConfiguredPeriodStillCountsDuration() {
        AttendanceService service = service();
        LocalDateTime checkIn = LocalDateTime.of(2026, 7, 16, 20, 0);
        LocalDateTime checkOut = checkIn.plusHours(2);
        when(records.findById(41L)).thenReturn(Optional.of(
                record(41L, checkIn, checkOut, "APPROVED", "APPROVED", false)
        ));

        service.recompute(41L);

        verify(records).updateEffective(41L, 120, 2, "VALID");
    }

    @Test
    void enforcedDutyPeriodKeepsAnApprovedOutsideRecordInvalid() {
        AttendanceService service = service();
        LocalDateTime checkIn = LocalDateTime.of(2026, 8, 10, 20, 0);
        LocalDateTime checkOut = checkIn.plusHours(2);
        when(records.findById(42L)).thenReturn(Optional.of(
                record(42L, 1L, Role.MEMBER, checkIn, checkOut,
                        "APPROVED", "APPROVED", true, false, false, true)
        ));

        service.recompute(42L);

        verify(records).updateEffective(42L, 0, 0, "INVALID");
    }

    @Test
    void publicCheckInSnapshotsTheCurrentEligibilityRules() {
        AttendanceService service = service();
        UserSummary member = user(1L, "20230001", "张三", Role.MEMBER);
        when(policies.current()).thenReturn(new AttendancePolicyService.AttendancePolicy(true, true));
        when(weekdays.isDutyWeekday(anyInt())).thenReturn(false);
        when(periods.contains(any())).thenReturn(false);
        when(users.findActiveByStudentNo("20230001")).thenReturn(Optional.of(member));
        when(records.findOpenToday(eq(1L), any(LocalDate.class))).thenReturn(Optional.empty());
        when(records.insertCheckIn(eq(1L), eq("20230001"), eq("张三"), any(LocalDate.class), anyInt(),
                eq(false), eq(false), eq(true), eq(true), any(Timestamp.class),
                eq("PENDING"), eq("INCOMPLETE"))).thenReturn(43L);
        when(records.findById(43L)).thenReturn(Optional.of(
                record(43L, 1L, Role.MEMBER, LocalDateTime.now(), null,
                        "PENDING", "NOT_SUBMITTED", false, false, true, true)
        ));

        AttendanceService.SubmitResponse response = service.submitPublic("20230001", "strict-policy-check-in");

        assertThat(response.message()).contains("不计入有效时长");
        verify(records).updateEffective(43L, 0, 0, "INVALID");
    }

    @Test
    void repeatedPublicSubmissionReturnsTheOriginalReceiptWithoutTogglingAttendance() {
        AttendanceService service = service();
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

    @Test
    void sameNameLookupReturnsMaskedAccountsAndOpaqueSelectionTokens() {
        AttendanceService service = service();
        UserSummary first = new UserSummary(
                1L, "20230001", "张三", Role.MEMBER, "ACTIVE",
                null, null, "2023级", null, false, LocalDateTime.now(), LocalDateTime.now()
        );
        UserSummary second = new UserSummary(
                2L, "20240002", "张三", Role.MEMBER, "ACTIVE",
                null, null, "2024级", null, false, LocalDateTime.now(), LocalDateTime.now()
        );
        when(weekdays.isDutyWeekday(anyInt())).thenReturn(true);
        when(periods.contains(any())).thenReturn(true);
        when(users.findActiveByStudentNo("张三")).thenReturn(Optional.empty());
        when(users.findActiveByName("张三")).thenReturn(List.of(first, second));
        when(selections.issue("20230001")).thenReturn("sel_11111111111111111111111111111111");
        when(selections.issue("20240002")).thenReturn("sel_22222222222222222222222222222222");

        AttendanceService.PublicLookupResponse response = service.lookupByInput("张三");

        assertThat(response.matches()).extracting(AttendanceService.PublicMemberOption::maskedStudentNo)
                .containsExactly("2023****0001", "2024****0002");
        assertThat(response.matches()).extracting(AttendanceService.PublicMemberOption::memberToken)
                .allMatch(token -> token.startsWith("sel_"));
    }

    @Test
    void oneCharacterNameLookupIsRejectedBeforeNameSearch() {
        AttendanceService service = service();
        when(weekdays.isDutyWeekday(anyInt())).thenReturn(true);
        when(periods.contains(any())).thenReturn(true);
        when(users.findActiveByStudentNo("张")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lookupByInput("张"))
                .isInstanceOf(com.ca.attendance.common.ApiException.class)
                .hasMessageContaining("至少输入 2 个字");
    }

    private AttendanceService service() {
        return new AttendanceService(users, records, weekdays, periods, logs, backups, submissions, selections, policies);
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
        return record(id, userId, userRole, checkInTime, checkOutTime,
                checkInStatus, checkOutStatus, true, withinDutyPeriod, false, false);
    }

    private AttendanceRecord record(long id, long userId, Role userRole,
                                    LocalDateTime checkInTime, LocalDateTime checkOutTime,
                                    String checkInStatus, String checkOutStatus,
                                    boolean dutyDay, boolean withinDutyPeriod,
                                    boolean requireDutyDay, boolean requireDutyPeriod) {
        return new AttendanceRecord(
                id,
                userId,
                userRole,
                "20230001",
                "张三",
                checkInTime.toLocalDate(),
                checkInTime.getDayOfWeek().getValue(),
                dutyDay,
                withinDutyPeriod,
                requireDutyDay,
                requireDutyPeriod,
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
