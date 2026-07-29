package com.ca.attendance.attendance;

import com.ca.attendance.access.RolePermissionPolicy;
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
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AttendanceService {
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository users;
    private final AttendanceRepository records;
    private final DutyWeekdayService weekdays;
    private final DutyPeriodService periods;
    private final OperationLogService logs;
    private final BackupService backups;
    private final PublicSubmissionRepository submissions;
    private final PublicMemberSelectionService selections;
    public AttendanceService(UserRepository users, AttendanceRepository records, DutyWeekdayService weekdays,
                             DutyPeriodService periods, OperationLogService logs, BackupService backups,
                             PublicSubmissionRepository submissions, PublicMemberSelectionService selections) {
        this.users = users;
        this.records = records;
        this.weekdays = weekdays;
        this.periods = periods;
        this.logs = logs;
        this.backups = backups;
        this.submissions = submissions;
        this.selections = selections;
    }

    public PublicLookupResponse lookup(String studentNo) {
        LocalDate today = LocalDate.now();
        int weekday = today.getDayOfWeek().getValue();
        boolean dutyDay = weekdays.isDutyWeekday(weekday);
        boolean withinDutyPeriod = periods.contains(java.time.LocalTime.now());
        UserSummary user = users.findActiveByStudentNo(studentNo).orElse(null);
        if (user == null) {
            return missingLookup(dutyDay, withinDutyPeriod, "学号不存在或账号已停用");
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
            return missingLookup(dutyDay, withinDutyPeriod, "请输入学号或姓名");
        }
        if (keyword.length() > 128) {
            throw ApiException.badRequest("查询内容过长");
        }
        if (keyword.startsWith("sel_")) {
            UserSummary selected = users.findActiveByStudentNo(selections.resolve(keyword))
                    .orElseThrow(() -> ApiException.badRequest("身份确认已失效，请重新查询"));
            return lookupResponse(selected, today, dutyDay, withinDutyPeriod);
        }

        var byStudentNo = users.findActiveByStudentNo(keyword);
        if (byStudentNo.isPresent()) {
            return lookupResponse(byStudentNo.get(), today, dutyDay, withinDutyPeriod);
        }

        if (keyword.length() < 2) {
            throw ApiException.badRequest("按姓名查询时至少输入 2 个字");
        }
        List<UserSummary> sameNameUsers = users.findActiveByName(keyword);
        if (sameNameUsers.isEmpty()) {
            return missingLookup(dutyDay, withinDutyPeriod, "未找到该学号或姓名，或账号已停用");
        }
        if (sameNameUsers.size() == 1) {
            return lookupResponse(sameNameUsers.get(0), today, dutyDay, withinDutyPeriod);
        }

        List<PublicMemberOption> matches = sameNameUsers.stream()
                .map(user -> new PublicMemberOption(
                        selections.issue(user.studentNo()),
                        maskStudentNo(user.studentNo()),
                        user.name(),
                        user.grade()
                ))
                .toList();
        String message = dutyDay && withinDutyPeriod
                ? "找到多位同名成员，请选择自己的账号"
                : "当前不在值班时间，仍可选择成员签到签退";
        return new PublicLookupResponse(false, dutyDay, withinDutyPeriod, null, null, null, null, message, matches);
    }

    private PublicLookupResponse lookupResponse(UserSummary user, LocalDate today, boolean dutyDay, boolean withinDutyPeriod) {
        String action = records.findOpenToday(user.id(), today).isPresent() ? "CHECK_OUT" : "CHECK_IN";
        String message = dutyDay && withinDutyPeriod
                ? "请确认姓名后提交"
                : "当前不在值班时间，仍可提交，是否计入有效时长由审核结果决定";
        return new PublicLookupResponse(
                true,
                dutyDay,
                withinDutyPeriod,
                selections.issue(user.studentNo()),
                maskStudentNo(user.studentNo()),
                user.name(),
                action,
                message,
                List.of()
        );
    }

    private PublicLookupResponse missingLookup(boolean dutyDay, boolean withinDutyPeriod, String message) {
        return new PublicLookupResponse(false, dutyDay, withinDutyPeriod, null, null, null, null, message, List.of());
    }

    private String maskStudentNo(String studentNo) {
        String value = studentNo == null ? "" : studentNo.trim();
        if (value.length() <= 4) {
            return "****" + value;
        }
        int prefixLength = Math.min(4, value.length() - 4);
        return value.substring(0, prefixLength) + "****" + value.substring(value.length() - 4);
    }

    public SubmitResponse submitPublic(String studentNo) {
        return submitPublic(studentNo, UUID.randomUUID().toString());
    }

    @Transactional
    public SubmitResponse submitPublicSelection(String memberToken, String requestId) {
        String normalizedRequestId = normalizeRequestId(requestId);
        String studentNo = selections.bindForSubmission(memberToken, normalizedRequestId);
        return submitPublic(studentNo, normalizedRequestId);
    }

    @Transactional
    public SubmitResponse submitPublic(String studentNo, String requestId) {
        String normalizedStudentNo = studentNo == null ? "" : studentNo.trim();
        String normalizedRequestId = normalizeRequestId(requestId);
        var previous = submissions.findByRequestId(normalizedRequestId);
        if (previous.isPresent()) {
            PublicSubmissionRepository.Receipt receipt = previous.get();
            if (!receipt.studentNo().equals(normalizedStudentNo)) {
                throw ApiException.conflict("该提交编号已用于其他成员，请重新查询后再试");
            }
            return responseFromReceipt(receipt);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        int weekday = today.getDayOfWeek().getValue();
        boolean dutyDay = weekdays.isDutyWeekday(weekday);
        boolean withinDutyPeriod = periods.contains(now.toLocalTime());
        UserSummary user = users.findActiveByStudentNo(normalizedStudentNo)
                .orElseThrow(() -> ApiException.notFound("学号不存在或账号已停用"));
        boolean autoApproved = user.role() == Role.MINISTER || user.role() == Role.PRESIDENT || user.role() == Role.ADMIN;
        String pendingOrAuto = autoApproved ? ReviewStatus.AUTO_APPROVED.name() : ReviewStatus.PENDING.name();

        var open = records.findOpenToday(user.id(), today);
        if (open.isEmpty()) {
            long id = records.insertCheckIn(
                    user.id(), user.studentNo(), user.name(), today, weekday, dutyDay, withinDutyPeriod,
                    Timestamp.valueOf(now), pendingOrAuto, "INCOMPLETE");
            recompute(id);
            SubmitResponse response = new SubmitResponse(id, "CHECK_IN", maskStudentNo(user.studentNo()), user.name(), now, pendingOrAuto,
                    submissionMessage("签到", dutyDay, withinDutyPeriod));
            saveSubmissionReceipt(normalizedRequestId, user.studentNo(), response);
            return response;
        }

        AttendanceRecord record = open.get();
        records.updateCheckOut(record.id(), Timestamp.valueOf(now), pendingOrAuto);
        recompute(record.id());
        SubmitResponse response = new SubmitResponse(record.id(), "CHECK_OUT", maskStudentNo(user.studentNo()), user.name(), now, pendingOrAuto,
                submissionMessage("签退", record.dutyDay(), record.withinDutyPeriod()));
        saveSubmissionReceipt(normalizedRequestId, user.studentNo(), response);
        return response;
    }

    private String normalizeRequestId(String requestId) {
        String normalized = requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId.trim();
        if (!normalized.matches("[A-Za-z0-9_-]{8,80}")) {
            throw ApiException.badRequest("提交编号格式不正确，请重新查询后再试");
        }
        return normalized;
    }

    private void saveSubmissionReceipt(String requestId, String studentNo, SubmitResponse response) {
        submissions.save(new PublicSubmissionRepository.Receipt(
                requestId,
                studentNo,
                response.recordId(),
                response.action(),
                response.name(),
                response.submittedAt(),
                response.status(),
                response.message()
        ));
    }

    private SubmitResponse responseFromReceipt(PublicSubmissionRepository.Receipt receipt) {
        return new SubmitResponse(
                receipt.recordId(),
                receipt.action(),
                maskStudentNo(receipt.studentNo()),
                receipt.name(),
                receipt.submittedAt(),
                receipt.reviewStatus(),
                receipt.message()
        );
    }

    public List<AttendanceRecord> pending() {
        AuthUser current = AuthContext.current();
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.ATTENDANCE_MANAGE,
                "无权查看待审核记录");
        return records.pendingForReviewer(current.id(), current.role() == Role.MINISTER);
    }

    public List<AttendanceRecord> openRecords(LocalDate from, LocalDate to) {
        RolePermissionPolicy.require(AuthContext.current().role(),
                RolePermissionPolicy.Permission.ATTENDANCE_MANAGE,
                "无权查看未签退记录");
        if (from.isAfter(to)) {
            throw ApiException.badRequest("开始日期不能晚于结束日期");
        }
        return records.openRecords(from, to);
    }

    @Transactional
    public void review(long id, String part, String action, String reason) {
        AuthUser current = AuthContext.current();
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.ATTENDANCE_MANAGE,
                "无权审核");
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

    @Transactional
    public BulkReviewResult bulkReview(BulkReviewRequest request) {
        RolePermissionPolicy.require(AuthContext.current().role(),
                RolePermissionPolicy.Permission.ATTENDANCE_MANAGE,
                "无权审核");
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
        RolePermissionPolicy.require(AuthContext.current().role(),
                RolePermissionPolicy.Permission.ATTENDANCE_MANAGE,
                "无权查看全部记录");
        validateDateRange(from, to);
        return records.search(from, to, studentNo, status);
    }

    public AttendanceRepository.AttendancePage searchPage(LocalDate from, LocalDate to, String studentNo,
                                                          String status, int page, int pageSize) {
        RolePermissionPolicy.require(AuthContext.current().role(),
                RolePermissionPolicy.Permission.ATTENDANCE_MANAGE,
                "无权查看全部记录");
        validateDateRange(from, to);
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        return records.searchPage(from, to, studentNo, status, safePage, safePageSize);
    }

    public List<UserRepository.UserCandidate> manualCandidates(String keyword) {
        RolePermissionPolicy.require(AuthContext.current().role(),
                RolePermissionPolicy.Permission.ATTENDANCE_CREATE,
                "只有会长或管理员可以选择补录账号");
        return users.searchActiveCandidates(keyword == null ? "" : keyword.trim(), 1000);
    }

    public List<AttendanceRecord> myRecords(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        long userId = AuthContext.current().id();
        return records.searchForUser(userId, from, to);
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("开始日期不能晚于结束日期");
        }
    }

    @Transactional
    public AttendanceRecord manualUpdate(long id, ManualUpdateRequest request) {
        AuthUser current = AuthContext.current();
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.ATTENDANCE_MANAGE,
                "只有部长、会长或管理员可以修改签到记录");
        if (request.reason() == null || request.reason().isBlank()) {
            throw ApiException.badRequest("手动修改必须填写原因");
        }
        if (request.checkInTime() == null) {
            throw ApiException.badRequest("请填写签到时间");
        }
        if (request.checkOutTime() != null && !request.checkOutTime().isAfter(request.checkInTime())) {
            throw ApiException.badRequest("签退时间必须晚于签到时间");
        }
        AttendanceRecord before = records.findById(id).orElseThrow(() -> ApiException.notFound("记录不存在"));
        if (current.role() == Role.MINISTER) {
            requireMinisterRecordAccess(before, request.checkInTime().toLocalDate());
        }
        LocalDate dutyDate = request.checkInTime().toLocalDate();
        int dutyWeekday = dutyDate.getDayOfWeek().getValue();
        boolean dutyDay = weekdays.isDutyWeekday(dutyWeekday);
        boolean withinDutyPeriod = periods.contains(request.checkInTime().toLocalTime());
        String checkInStatus = current.role() == Role.MINISTER
                ? ReviewStatus.AUTO_APPROVED.name()
                : normalizeReviewStatus(request.checkInStatus(), "签到审核状态");
        String checkOutStatus;
        if (request.checkOutTime() == null) {
            checkOutStatus = ReviewStatus.NOT_SUBMITTED.name();
        } else if (current.role() == Role.MINISTER) {
            checkOutStatus = ReviewStatus.AUTO_APPROVED.name();
        } else {
            checkOutStatus = normalizeReviewStatus(request.checkOutStatus(), "签退审核状态");
        }
        records.manualUpdate(
                id, dutyDate, dutyWeekday, dutyDay, withinDutyPeriod,
                Timestamp.valueOf(request.checkInTime()),
                request.checkOutTime() == null ? null : Timestamp.valueOf(request.checkOutTime()),
                checkInStatus, checkOutStatus, request.reason().trim(), current.id());
        recompute(id);
        AttendanceRecord after = records.findById(id).orElseThrow();
        logs.log("MANUAL_UPDATE_ATTENDANCE", "attendance_records", id, before, after, request.reason());
        return after;
    }

    @Transactional
    public AttendanceRecord manualCreate(ManualCreateRequest request) {
        AuthUser current = AuthContext.current();
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.ATTENDANCE_CREATE,
                "只有会长或管理员可以添加签到记录");
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
                user.id(), user.studentNo(), user.name(), dutyDate, weekday,
                Timestamp.valueOf(request.checkInTime()),
                request.checkOutTime() == null ? null : Timestamp.valueOf(request.checkOutTime()),
                ReviewStatus.AUTO_APPROVED.name(), checkOutStatus, request.reason().trim(), current.id());
        recompute(id);
        AttendanceRecord created = records.findById(id).orElseThrow();
        logs.log("MANUAL_CREATE_ATTENDANCE", "attendance_records", id, null, created, request.reason());
        return created;
    }

    @Transactional
    public void delete(long id, String reason) {
        AuthUser current = AuthContext.current();
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.ATTENDANCE_MANAGE,
                "只有部长、会长或管理员可以删除签到记录");
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("删除签到记录必须填写原因");
        }
        AttendanceRecord before = records.findById(id).orElseThrow(() -> ApiException.notFound("记录不存在"));
        if (current.role() == Role.MINISTER) {
            requireMinisterRecordAccess(before, before.dutyDate());
        }
        BackupService.BackupItem safetyBackup = backups.createSystemBackup(
                current.name() + "（" + current.studentNo() + "）删除签到记录 #" + id
        );
        records.delete(id);
        logs.log("DELETE_ATTENDANCE_RECORD", "attendance_records", id, before, null,
                reason.trim() + "；删除前自动备份：" + safetyBackup.filename());
    }

    private void requireMinisterRecordAccess(AttendanceRecord record, LocalDate updatedDutyDate) {
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        if (record.dutyDate().isBefore(weekStart) || record.dutyDate().isAfter(weekEnd)
                || updatedDutyDate.isBefore(weekStart) || updatedDutyDate.isAfter(weekEnd)) {
            throw ApiException.forbidden("部长只能修改或删除本周签到记录");
        }
        Role targetRole = record.userRole();
        if (targetRole == Role.PRESIDENT || targetRole == Role.ADMIN) {
            throw ApiException.forbidden("部长不能修改或删除会长或管理员的签到记录");
        }
    }

    private String normalizeReviewStatus(String status, String fieldName) {
        try {
            return ReviewStatus.valueOf(status == null ? "" : status.trim().toUpperCase()).name();
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(fieldName + "不正确");
        }
    }

    public void recompute(long id) {
        AttendanceRecord record = records.findById(id).orElseThrow(() -> ApiException.notFound("记录不存在"));
        if (ReviewStatus.REJECTED.name().equals(record.checkInStatus())
                || ReviewStatus.REJECTED.name().equals(record.checkOutStatus())) {
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
            return action + "已提交；今日不是值班日，是否计入有效时长由审核结果决定";
        }
        if (!withinDutyPeriod) {
            return action + "已提交；当前不在值班时段，是否计入有效时长由审核结果决定";
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
            String memberToken,
            String maskedStudentNo,
            String name,
            String action,
            String message,
            List<PublicMemberOption> matches
    ) {
    }

    public record PublicMemberOption(
            String memberToken,
            String maskedStudentNo,
            String name,
            String grade
    ) {
    }

    public record SubmitResponse(
            long recordId,
            String action,
            String maskedStudentNo,
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
