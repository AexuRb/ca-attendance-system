package com.ca.attendance.attendance;

import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.common.ReviewStatus;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.maintenance.BackupService;
import com.ca.attendance.settings.DutyPeriodService;
import com.ca.attendance.settings.DutyWeekdayService;
import com.ca.attendance.user.UserRepository;
import com.ca.attendance.user.UserSummary;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AttendanceService {
    private final UserRepository users;
    private final AttendanceRepository records;
    private final DutyWeekdayService weekdays;
    private final DutyPeriodService periods;
    private final OperationLogService logs;
    private final BackupService backups;

    public AttendanceService(UserRepository users, AttendanceRepository records, DutyWeekdayService weekdays,
                             DutyPeriodService periods, OperationLogService logs, BackupService backups) {
        this.users = users;
        this.records = records;
        this.weekdays = weekdays;
        this.periods = periods;
        this.logs = logs;
        this.backups = backups;
    }

    public PublicLookupResponse lookup(String studentNo) {
        LocalDate today = LocalDate.now();
        int weekday = today.getDayOfWeek().getValue();
        boolean dutyDay = weekdays.isDutyWeekday(weekday);
        boolean withinDutyPeriod = periods.contains(java.time.LocalTime.now());
        UserSummary user = users.findActiveByStudentNo(studentNo).orElse(null);
        if (user == null) {
            return new PublicLookupResponse(false, dutyDay, withinDutyPeriod, null, null, null, "学号不存在或账号已停用", List.of());
        }
        return lookupResponse(user, today, dutyDay, withinDutyPeriod);
    }

    public PublicLookupResponse lookupByInput(String input) {
        LocalDate today = LocalDate.now();
        int weekday = today.getDayOfWeek().getValue();
        boolean dutyDay = weekdays.isDutyWeekday(weekday);
        boolean withinDutyPeriod = periods.contains(java.time.LocalTime.now());
        String keyword = input == null ? "" : input.trim();
        if (keyword.isBlank()) {
            return new PublicLookupResponse(false, dutyDay, withinDutyPeriod, null, null, null, "请输入学号或姓名", List.of());
        }

        var byStudentNo = users.findActiveByStudentNo(keyword);
        if (byStudentNo.isPresent()) {
            return lookupResponse(byStudentNo.get(), today, dutyDay, withinDutyPeriod);
        }

        List<UserSummary> sameNameUsers = users.findActiveByName(keyword);
        if (sameNameUsers.isEmpty()) {
            return new PublicLookupResponse(false, dutyDay, withinDutyPeriod, null, null, null, "未找到该学号或姓名，或账号已停用", List.of());
        }
        if (sameNameUsers.size() == 1) {
            return lookupResponse(sameNameUsers.get(0), today, dutyDay, withinDutyPeriod);
        }

        List<PublicMemberOption> matches = sameNameUsers.stream()
                .map(user -> new PublicMemberOption(user.studentNo(), user.name(), user.grade(), user.major()))
                .toList();
        String message = dutyDay ? "找到多位同名成员，请选择自己的学号" : "今日非值班日，也可选择成员测试签到签退";
        return new PublicLookupResponse(false, dutyDay, withinDutyPeriod, null, null, null, message, matches);
    }

    private PublicLookupResponse lookupResponse(UserSummary user, LocalDate today, boolean dutyDay, boolean withinDutyPeriod) {
        String action = records.findOpenToday(user.id(), today).isPresent() ? "CHECK_OUT" : "CHECK_IN";
        String message = dutyDay ? "请确认姓名后提交" : "今日非值班日，可测试签到签退";
        return new PublicLookupResponse(true, dutyDay, withinDutyPeriod, user.studentNo(), user.name(), action, message, List.of());
    }

    public SubmitResponse submitPublic(String studentNo) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        int weekday = today.getDayOfWeek().getValue();
        boolean dutyDay = weekdays.isDutyWeekday(weekday);
        boolean withinDutyPeriod = periods.contains(now.toLocalTime());
        UserSummary user = users.findActiveByStudentNo(studentNo)
                .orElseThrow(() -> ApiException.notFound("学号不存在或账号已停用"));
        boolean autoApproved = user.role() == Role.PRESIDENT || user.role() == Role.ADMIN;
        String pendingOrAuto = autoApproved ? ReviewStatus.AUTO_APPROVED.name() : ReviewStatus.PENDING.name();

        var open = records.findOpenToday(user.id(), today);
        if (open.isEmpty()) {
            long id = records.insertCheckIn(
                    user.id(),
                    user.studentNo(),
                    user.name(),
                    today,
                    weekday,
                    dutyDay,
                    withinDutyPeriod,
                    Timestamp.valueOf(now),
                    pendingOrAuto,
                    "INCOMPLETE"
            );
            recompute(id);
            return new SubmitResponse(id, "CHECK_IN", user.studentNo(), user.name(), now, pendingOrAuto,
                    submissionMessage("签到", dutyDay, withinDutyPeriod));
        }

        AttendanceRecord record = open.get();
        records.updateCheckOut(record.id(), Timestamp.valueOf(now), pendingOrAuto);
        recompute(record.id());
        return new SubmitResponse(record.id(), "CHECK_OUT", user.studentNo(), user.name(), now, pendingOrAuto,
                submissionMessage("签退", record.dutyDay(), record.withinDutyPeriod()));
    }

    public List<AttendanceRecord> pending() {
        AuthUser current = AuthContext.current();
        if (!current.role().atLeastManager()) {
            throw ApiException.forbidden("无权查看待审核记录");
        }
        return records.pendingForReviewer(current.id(), current.role() == Role.MINISTER);
    }

    public List<AttendanceRecord> openRecords(LocalDate from, LocalDate to) {
        if (!AuthContext.current().role().atLeastManager()) {
            throw ApiException.forbidden("无权查看未签退记录");
        }
        if (from.isAfter(to)) {
            throw ApiException.badRequest("开始日期不能晚于结束日期");
        }
        return records.openRecords(from, to);
    }

    public void review(long id, String part, String action, String reason) {
        AuthUser current = AuthContext.current();
        if (!current.role().atLeastManager()) {
            throw ApiException.forbidden("无权审核");
        }
        AttendanceRecord record = records.findById(id).orElseThrow(() -> ApiException.notFound("记录不存在"));
        if (current.role() == Role.MINISTER && current.id() == record.userId()) {
            throw ApiException.forbidden("部长不能审核自己的记录");
        }
        String normalizedPart = normalizePart(part);
        String status = switch (action.toUpperCase()) {
            case "APPROVE" -> ReviewStatus.APPROVED.name();
            case "REJECT" -> ReviewStatus.REJECTED.name();
            default -> throw ApiException.badRequest("审核动作只能是 APPROVE 或 REJECT");
        };
        if ("CHECK_IN".equals(normalizedPart) && !ReviewStatus.PENDING.name().equals(record.checkInStatus())) {
            throw ApiException.badRequest("签到记录不是待审核状态");
        }
        if ("CHECK_OUT".equals(normalizedPart) && !ReviewStatus.PENDING.name().equals(record.checkOutStatus())) {
            throw ApiException.badRequest("签退记录不是待审核状态");
        }
        if (ReviewStatus.REJECTED.name().equals(status) && (reason == null || reason.isBlank())) {
            throw ApiException.badRequest("驳回时必须填写原因");
        }
        records.updateReview(id, normalizedPart, status, current.id(), reason);
        recompute(id);
        AttendanceRecord after = records.findById(id).orElseThrow();
        logs.log("REVIEW_ATTENDANCE", "attendance_records", id, record, after, reviewReason(normalizedPart, status, reason));
    }

    public BulkReviewResult bulkReview(BulkReviewRequest request) {
        if (!AuthContext.current().role().atLeastManager()) {
            throw ApiException.forbidden("无权审核");
        }
        if (request.ids() == null || request.ids().isEmpty()) {
            throw ApiException.badRequest("请选择要审核的记录");
        }

        List<String> parts = bulkParts(request.part());
        int reviewed = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (Long id : request.ids().stream().filter(item -> item != null && item > 0).distinct().limit(500).toList()) {
            AttendanceRecord first = records.findById(id).orElse(null);
            if (first == null) {
                skipped++;
                errors.add("记录 #" + id + " 不存在");
                continue;
            }

            boolean touched = false;
            for (String part : parts) {
                AttendanceRecord current = records.findById(id).orElse(null);
                if (current == null) {
                    break;
                }
                String status = "CHECK_IN".equals(part) ? current.checkInStatus() : current.checkOutStatus();
                if (!ReviewStatus.PENDING.name().equals(status)) {
                    continue;
                }
                try {
                    review(id, part, "APPROVE", "批量审核通过");
                    reviewed++;
                    touched = true;
                } catch (ApiException ex) {
                    errors.add(first.name() + "（" + first.studentNo() + "）：" + ex.getMessage());
                }
            }
            if (!touched) {
                skipped++;
            }
        }
        return new BulkReviewResult(reviewed, skipped, errors);
    }

    public List<AttendanceRecord> search(LocalDate from, LocalDate to, String studentNo, String status) {
        if (!AuthContext.current().role().atLeastManager()) {
            throw ApiException.forbidden("无权查看全部记录");
        }
        return records.search(from, to, studentNo, status);
    }

    public List<AttendanceRecord> myRecords(LocalDate from, LocalDate to) {
        long userId = AuthContext.current().id();
        return records.search(from, to, "", "").stream().filter(r -> r.userId() == userId).toList();
    }

    public AttendanceRecord manualUpdate(long id, ManualUpdateRequest request) {
        AuthUser current = AuthContext.current();
        if (current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有管理员可以手动修改签到记录");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw ApiException.badRequest("手动修改必须填写原因");
        }
        AttendanceRecord before = records.findById(id).orElseThrow(() -> ApiException.notFound("记录不存在"));
        records.manualUpdate(
                id,
                Timestamp.valueOf(request.checkInTime()),
                request.checkOutTime() == null ? null : Timestamp.valueOf(request.checkOutTime()),
                request.checkInStatus(),
                request.checkOutStatus(),
                request.reason(),
                current.id()
        );
        recompute(id);
        AttendanceRecord after = records.findById(id).orElseThrow();
        logs.log("MANUAL_UPDATE_ATTENDANCE", "attendance_records", id, before, after, request.reason());
        return after;
    }

    public AttendanceRecord manualCreate(ManualCreateRequest request) {
        AuthUser current = AuthContext.current();
        if (current.role() != Role.PRESIDENT && current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有会长或管理员可以添加签到记录");
        }
        if (request.studentNo() == null || request.studentNo().isBlank()) {
            throw ApiException.badRequest("请填写学号");
        }
        if (request.checkInTime() == null) {
            throw ApiException.badRequest("请填写签到时间");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw ApiException.badRequest("添加签到记录必须填写原因");
        }
        if (request.checkOutTime() != null && !request.checkOutTime().isAfter(request.checkInTime())) {
            throw ApiException.badRequest("签退时间必须晚于签到时间");
        }

        UserSummary user = users.findActiveByStudentNo(request.studentNo().trim())
                .orElseThrow(() -> ApiException.notFound("学号不存在或账号已停用"));
        LocalDate dutyDate = request.checkInTime().toLocalDate();
        int weekday = dutyDate.getDayOfWeek().getValue();
        boolean dutyDay = weekdays.isDutyWeekday(weekday);
        if (!dutyDay) {
            throw ApiException.badRequest("所选日期不是当前设置的值班日，不能添加有效签到记录");
        }

        String checkOutStatus = request.checkOutTime() == null
                ? ReviewStatus.NOT_SUBMITTED.name()
                : ReviewStatus.AUTO_APPROVED.name();
        long id = records.insertManual(
                user.id(),
                user.studentNo(),
                user.name(),
                dutyDate,
                weekday,
                Timestamp.valueOf(request.checkInTime()),
                request.checkOutTime() == null ? null : Timestamp.valueOf(request.checkOutTime()),
                ReviewStatus.AUTO_APPROVED.name(),
                checkOutStatus,
                request.reason().trim(),
                current.id()
        );
        recompute(id);
        AttendanceRecord created = records.findById(id).orElseThrow();
        logs.log("MANUAL_CREATE_ATTENDANCE", "attendance_records", id, null, created, request.reason());
        return created;
    }

    public void delete(long id) {
        AuthUser current = AuthContext.current();
        if (current.role() != Role.PRESIDENT && current.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有会长或管理员可以删除签到记录");
        }
        AttendanceRecord before = records.findById(id).orElseThrow(() -> ApiException.notFound("记录不存在"));
        BackupService.BackupItem safetyBackup = backups.create();
        records.delete(id);
        logs.log("DELETE_ATTENDANCE_RECORD", "attendance_records", id, before, null,
                "会长或管理员删除签到记录；删除前自动备份：" + safetyBackup.filename());
    }

    public void recompute(long id) {
        AttendanceRecord record = records.findById(id).orElseThrow(() -> ApiException.notFound("记录不存在"));
        if (ReviewStatus.REJECTED.name().equals(record.checkInStatus())
                || ReviewStatus.REJECTED.name().equals(record.checkOutStatus())
                || !record.dutyDay()
                || !record.withinDutyPeriod()) {
            records.updateEffective(id, 0, 0, "INVALID");
            return;
        }
        if (record.checkOutTime() == null || ReviewStatus.NOT_SUBMITTED.name().equals(record.checkOutStatus())) {
            records.updateEffective(id, 0, 0, "INCOMPLETE");
            return;
        }
        boolean checkInOk = approved(record.checkInStatus());
        boolean checkOutOk = approved(record.checkOutStatus());
        if (!checkInOk || !checkOutOk) {
            records.updateEffective(id, 0, 0, "PENDING");
            return;
        }
        Duration duration = Duration.between(record.checkInTime(), record.checkOutTime());
        if (duration.toSeconds() <= 0) {
            records.updateEffective(id, 0, 0, "INVALID");
            return;
        }
        long minutes = duration.toMinutes();
        int validHours = (int) ((minutes + 30) / 60);
        records.updateEffective(id, (int) minutes, validHours, "VALID");
    }

    private boolean approved(String status) {
        return ReviewStatus.APPROVED.name().equals(status) || ReviewStatus.AUTO_APPROVED.name().equals(status);
    }

    private String submissionMessage(String action, boolean dutyDay, boolean withinDutyPeriod) {
        if (!dutyDay) {
            return action + "已提交；今日不是值班日，记录默认不计入有效时长";
        }
        if (!withinDutyPeriod) {
            return action + "已提交；当前不在值班时段，记录默认不计入有效时长";
        }
        return action + "提交成功";
    }

    private String reviewReason(String part, String status, String reason) {
        if (reason != null && !reason.isBlank()) {
            return reason.trim();
        }
        String partText = "CHECK_IN".equals(part) ? "签到" : "签退";
        String statusText = ReviewStatus.APPROVED.name().equals(status) ? "通过" : "驳回";
        return partText + "审核" + statusText;
    }

    private String normalizePart(String part) {
        String normalized = part == null ? "" : part.trim().toUpperCase();
        if (!normalized.equals("CHECK_IN") && !normalized.equals("CHECK_OUT")) {
            throw ApiException.badRequest("审核部分只能是 CHECK_IN 或 CHECK_OUT");
        }
        return normalized;
    }

    private List<String> bulkParts(String part) {
        String normalized = part == null ? "" : part.trim().toUpperCase();
        if ("ALL".equals(normalized)) {
            return List.of("CHECK_IN", "CHECK_OUT");
        }
        return List.of(normalizePart(normalized));
    }

    public record PublicLookupResponse(
            boolean exists,
            boolean dutyDay,
            boolean withinDutyPeriod,
            String studentNo,
            String name,
            String action,
            String message,
            List<PublicMemberOption> matches
    ) {
    }

    public record PublicMemberOption(
            String studentNo,
            String name,
            String grade,
            String major
    ) {
    }

    public record SubmitResponse(
            long recordId,
            String action,
            String studentNo,
            String name,
            LocalDateTime submittedAt,
            String status,
            String message
    ) {
    }

    public record ManualUpdateRequest(
            LocalDateTime checkInTime,
            LocalDateTime checkOutTime,
            String checkInStatus,
            String checkOutStatus,
            String reason
    ) {
    }

    public record ManualCreateRequest(
            String studentNo,
            LocalDateTime checkInTime,
            LocalDateTime checkOutTime,
            String reason
    ) {
    }

    public record BulkReviewRequest(List<Long> ids, String part) {
    }

    public record BulkReviewResult(int reviewed, int skipped, List<String> errors) {
    }
}
