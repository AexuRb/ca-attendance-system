package com.ca.attendance.attendance;

import com.ca.attendance.access.RolePermissionPolicy;
import com.ca.attendance.auth.AuthContext;
import com.ca.attendance.auth.AuthUser;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.EffectiveStatus;
import com.ca.attendance.common.ExportRowLimit;
import com.ca.attendance.common.PaginationPolicy;
import com.ca.attendance.common.Role;
import com.ca.attendance.common.ReviewStatus;
import com.ca.attendance.log.OperationLogService;
import com.ca.attendance.maintenance.BackupService;
import com.ca.attendance.settings.DutyPeriodService;
import com.ca.attendance.settings.DutyWeekdayService;
import com.ca.attendance.settings.AttendancePolicyService;
import com.ca.attendance.user.UserRepository;
import com.ca.attendance.user.UserSummary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AttendanceService {
    private static final int BULK_REVIEW_BATCH_SIZE = 200;
    private static final long MANUAL_TIME_FUTURE_TOLERANCE_MINUTES = 5;

    private final UserRepository users;
    private final AttendanceRepository records;
    private final DutyWeekdayService weekdays;
    private final DutyPeriodService periods;
    private final OperationLogService logs;
    private final BackupService backups;
    private final PublicSubmissionRepository submissions;
    private final PublicSubmissionRetention submissionRetention;
    private final PublicMemberSelectionService selections;
    private final AttendancePolicyService policies;
    private final PublicSubmissionTransactionCoordinator submissionTransactions;
    private final Clock clock;
    public AttendanceService(UserRepository users, AttendanceRepository records, DutyWeekdayService weekdays,
                             DutyPeriodService periods, OperationLogService logs, BackupService backups,
                             PublicSubmissionRepository submissions, PublicSubmissionRetention submissionRetention,
                             PublicMemberSelectionService selections,
                             AttendancePolicyService policies,
                             PublicSubmissionTransactionCoordinator submissionTransactions,
                             Clock clock) {
        this.users = users;
        this.records = records;
        this.weekdays = weekdays;
        this.periods = periods;
        this.logs = logs;
        this.backups = backups;
        this.submissions = submissions;
        this.submissionRetention = submissionRetention;
        this.selections = selections;
        this.policies = policies;
        this.submissionTransactions = submissionTransactions;
        this.clock = clock;
    }

    public PublicLookupResponse lookupByInput(String input) {
        LocalDate today = LocalDate.now(clock);
        int weekday = today.getDayOfWeek().getValue();
        boolean dutyDay = weekdays.isDutyWeekday(weekday);
        boolean withinDutyPeriod = periods.contains(java.time.LocalTime.now(clock));
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
            return lookupResponse(selected, today, dutyDay, withinDutyPeriod, policies.current());
        }

        var byStudentNo = users.findActiveByStudentNo(keyword);
        if (byStudentNo.isPresent()) {
            return lookupResponse(byStudentNo.get(), today, dutyDay, withinDutyPeriod, policies.current());
        }

        if (keyword.length() < 2) {
            throw ApiException.badRequest("按姓名查询时至少输入 2 个字");
        }
        List<UserSummary> sameNameUsers = users.findActiveByName(keyword);
        if (sameNameUsers.isEmpty()) {
            return missingLookup(dutyDay, withinDutyPeriod, "未找到该学号或姓名，或账号已停用");
        }
        if (sameNameUsers.size() == 1) {
            return lookupResponse(sameNameUsers.get(0), today, dutyDay, withinDutyPeriod, policies.current());
        }

        List<PublicMemberOption> matches = sameNameUsers.stream()
                .map(user -> new PublicMemberOption(
                        selections.issue(user.studentNo()),
                        maskStudentNo(user.studentNo()),
                        user.name(),
                        user.grade()
                ))
                .toList();
        String message = lookupMessage(
                dutyDay,
                withinDutyPeriod,
                policies.current(),
                "找到多位同名成员，请选择自己的账号",
                "当前不在值班时间，仍可选择成员签到签退"
        );
        return new PublicLookupResponse(false, dutyDay, withinDutyPeriod, null, null, null, null, message, matches);
    }

    private PublicLookupResponse lookupResponse(UserSummary user, LocalDate today, boolean dutyDay,
                                                boolean withinDutyPeriod,
                                                AttendancePolicyService.AttendancePolicy policy) {
        String action = records.findOpenToday(user.id(), today).isPresent() ? "CHECK_OUT" : "CHECK_IN";
        String message = lookupMessage(
                dutyDay,
                withinDutyPeriod,
                policy,
                "请确认姓名后提交",
                "当前不在值班时间，仍可提交，是否计入有效时长由审核结果决定"
        );
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
        int visibleLength = 0;
        if (value.length() >= 8) {
            visibleLength = 4;
        } else if (value.length() >= 4) {
            visibleLength = 2;
        } else if (value.length() >= 2) {
            visibleLength = 1;
        }
        return "****" + value.substring(value.length() - visibleLength);
    }

    public SubmitResponse submitPublicSelection(String memberToken, String requestId) {
        String normalizedRequestId = normalizeRequestId(requestId);
        String studentNo = selections.bindForSubmission(memberToken, normalizedRequestId);
        return submitPublic(studentNo, normalizedRequestId);
    }

    public SubmitResponse submitPublic(String studentNo, String requestId) {
        String normalizedStudentNo = studentNo == null ? "" : studentNo.trim();
        String normalizedRequestId = normalizeRequestId(requestId);
        try {
            return submissionTransactions.execute(
                    normalizedRequestId,
                    () -> submitPublicTransaction(normalizedStudentNo, normalizedRequestId)
            );
        } catch (DuplicateKeyException conflict) {
            PublicSubmissionRepository.Receipt receipt = submissions.findByRequestId(normalizedRequestId)
                    .orElseThrow(() -> conflict);
            return responseFromReceipt(receipt, normalizedStudentNo);
        }
    }

    private SubmitResponse submitPublicTransaction(String normalizedStudentNo, String normalizedRequestId) {
        LocalDateTime now = LocalDateTime.now(clock);
        submissionRetention.cleanupExpired(now);
        var previous = submissions.findByRequestId(normalizedRequestId);
        if (previous.isPresent()) {
            return responseFromReceipt(previous.get(), normalizedStudentNo);
        }

        LocalDate today = now.toLocalDate();
        int weekday = today.getDayOfWeek().getValue();
        boolean dutyDay = weekdays.isDutyWeekday(weekday);
        boolean withinDutyPeriod = periods.contains(now.toLocalTime());
        UserSummary user = users.findActiveByStudentNo(normalizedStudentNo)
                .orElseThrow(() -> ApiException.notFound("学号不存在或账号已停用"));
        requireWriteLock(records.acquireUserAttendanceWriteLock(user.id()));
        boolean autoApproved = user.role() == Role.MINISTER || user.role() == Role.PRESIDENT || user.role() == Role.ADMIN;
        String pendingOrAuto = autoApproved ? ReviewStatus.AUTO_APPROVED.name() : ReviewStatus.PENDING.name();

        var open = records.findOpenToday(user.id(), today);
        if (open.isEmpty()) {
            AttendancePolicyService.AttendancePolicy policy = policies.current();
            long id = records.insertCheckIn(
                    user.id(), user.studentNo(), user.name(), today, weekday, dutyDay, withinDutyPeriod,
                    policy.requireDutyDay(), policy.requireDutyPeriod(),
                    Timestamp.valueOf(now), pendingOrAuto, "INCOMPLETE");
            recompute(id);
            SubmitResponse response = new SubmitResponse(id, "CHECK_IN", maskStudentNo(user.studentNo()), user.name(), now, pendingOrAuto,
                    submissionMessage("签到", dutyDay, withinDutyPeriod,
                            policy.requireDutyDay(), policy.requireDutyPeriod()));
            saveSubmissionReceipt(normalizedRequestId, user.studentNo(), response);
            return response;
        }

        AttendanceRecord record = open.get();
        records.updateCheckOut(record.id(), Timestamp.valueOf(now), pendingOrAuto);
        recompute(record.id());
        SubmitResponse response = new SubmitResponse(record.id(), "CHECK_OUT", maskStudentNo(user.studentNo()), user.name(), now, pendingOrAuto,
                submissionMessage("签退", record.dutyDay(), record.withinDutyPeriod(),
                        record.requireDutyDay(), record.requireDutyPeriod()));
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

    private SubmitResponse responseFromReceipt(
            PublicSubmissionRepository.Receipt receipt,
            String expectedStudentNo
    ) {
        if (!receipt.studentNo().equals(expectedStudentNo)) {
            throw ApiException.conflict("该提交编号已用于其他成员，请重新查询后再试");
        }
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
        requireReviewAccess(current);
        return records.pendingForReviewer(current.id(), current.role() == Role.MINISTER);
    }

    @Transactional(readOnly = true)
    public PendingReviewQueue pendingQueue() {
        AuthUser current = AuthContext.current();
        requireReviewAccess(current);
        boolean minister = current.role() == Role.MINISTER;
        AttendanceRepository.PendingReviewSummary summary = records.pendingSummary(current.id(), minister);
        List<AttendanceRecord> items = records.pendingForReviewer(current.id(), minister);
        return new PendingReviewQueue(
                items,
                summary.recordCount(),
                summary.itemCount(),
                summary.recordCount() > items.size()
        );
    }

    private void requireReviewAccess(AuthUser current) {
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.ATTENDANCE_MANAGE,
                "无权查看待审核记录");
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
        String storedRejectReason = ReviewStatus.REJECTED.name().equals(status) ? reason.trim() : null;
        records.updateReview(id, normalizedPart, status, current.id(), storedRejectReason);
        recompute(id);
        AttendanceRecord after = records.findById(id).orElseThrow();
        logs.log("REVIEW_ATTENDANCE", "attendance_records", id, record, after, reviewReason(normalizedPart, status, reason));
    }

    @Transactional
    public BulkReviewResult bulkReview(BulkReviewRequest request) {
        AuthUser current = AuthContext.current();
        RolePermissionPolicy.require(current.role(),
                RolePermissionPolicy.Permission.ATTENDANCE_MANAGE,
                "无权审核");
        boolean allPending = "ALL_PENDING".equals(normalizeBulkScope(request.scope()));
        Set<Long> requestedIds = validBulkIds(request.ids());
        if (!allPending && requestedIds.isEmpty()) {
            throw ApiException.badRequest("请选择要审核的记录");
        }

        List<String> parts = bulkParts(request.part());
        records.acquireReviewWriteLock(current.id());
        List<AttendanceRecord> candidates = allPending
                ? records.allPendingForReviewer(current.id(), current.role() == Role.MINISTER)
                : findBulkCandidates(requestedIds);
        int matched = candidates.size();
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        if (!allPending && requestedIds.size() > candidates.size()) {
            Set<Long> foundIds = candidates.stream().map(AttendanceRecord::id).collect(java.util.stream.Collectors.toSet());
            requestedIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .forEach(id -> errors.add("记录 #" + id + " 不存在"));
            skipped += requestedIds.size() - candidates.size();
        }

        List<Long> checkInIds = new ArrayList<>();
        List<Long> checkOutIds = new ArrayList<>();
        List<Long> touchedIds = new ArrayList<>();
        for (AttendanceRecord candidate : candidates) {
            if (current.role() == Role.MINISTER && current.id() == candidate.userId()) {
                skipped++;
                errors.add(candidate.name() + "（" + candidate.studentNo() + "）：部长不能审核自己的记录");
                continue;
            }
            boolean touched = false;
            if (parts.contains("CHECK_IN") && ReviewStatus.PENDING.name().equals(candidate.checkInStatus())) {
                checkInIds.add(candidate.id());
                touched = true;
            }
            if (parts.contains("CHECK_OUT") && ReviewStatus.PENDING.name().equals(candidate.checkOutStatus())) {
                checkOutIds.add(candidate.id());
                touched = true;
            }
            if (touched) {
                touchedIds.add(candidate.id());
            } else {
                skipped++;
            }
        }

        approveInBatches(checkInIds, current.id(), true);
        approveInBatches(checkOutIds, current.id(), false);
        recomputeInBatches(touchedIds, current.id());

        BulkReviewResult result = new BulkReviewResult(
                matched,
                checkInIds.size() + checkOutIds.size(),
                skipped,
                errors
        );
        logs.log(
                "BULK_REVIEW_ATTENDANCE",
                "attendance_records",
                null,
                Map.of(
                        "scope", allPending ? "ALL_PENDING" : "SELECTED",
                        "part", request.part(),
                        "requested", allPending ? matched : requestedIds.size()
                ),
                result,
                "批量审核通过"
        );
        return result;
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecord> search(LocalDate from, LocalDate to, String studentNo, String status) {
        RolePermissionPolicy.require(AuthContext.current().role(),
                RolePermissionPolicy.Permission.ATTENDANCE_MANAGE,
                "无权查看全部记录");
        validateDateRange(from, to);
        List<AttendanceRecord> result = records.search(from, to, studentNo, normalizeEffectiveStatusFilter(status));
        ExportRowLimit.requireWithinLimit(result.size(), "查询");
        return result;
    }

    @Transactional(readOnly = true)
    public AttendanceRepository.AttendancePage searchPage(LocalDate from, LocalDate to, String studentNo,
                                                          String status, int page, int pageSize) {
        RolePermissionPolicy.require(AuthContext.current().role(),
                RolePermissionPolicy.Permission.ATTENDANCE_MANAGE,
                "无权查看全部记录");
        validateDateRange(from, to);
        PaginationPolicy.PageRequest paging = PaginationPolicy.normalize(page, pageSize);
        return records.searchPage(from, to, studentNo, normalizeEffectiveStatusFilter(status), paging.page(), paging.pageSize());
    }

    public List<UserRepository.UserCandidate> manualCandidates(String keyword) {
        RolePermissionPolicy.require(AuthContext.current().role(),
                RolePermissionPolicy.Permission.ATTENDANCE_CREATE,
                "只有会长或管理员可以选择补录账号");
        return users.searchActiveCandidates(keyword == null ? "" : keyword.trim(), 1000);
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecord> myRecords(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        long userId = AuthContext.current().id();
        List<AttendanceRecord> result = records.searchForUser(userId, from, to);
        ExportRowLimit.requireWithinLimit(result.size(), "查询");
        return result;
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
        validateManualTimes(request.checkInTime(), request.checkOutTime());
        if (records.acquireAttendanceRecordWriteLock(id) != 1) {
            throw ApiException.notFound("记录不存在");
        }
        AttendanceRecord before = records.findById(id).orElseThrow(() -> ApiException.notFound("记录不存在"));
        if (current.role() == Role.MINISTER) {
            requireMinisterRecordAccess(before, request.checkInTime().toLocalDate());
            if (Boolean.TRUE.equals(request.recomputeSnapshot())) {
                throw ApiException.forbidden("只有会长或管理员可以按当前设置重新评估记录");
            }
        }
        rejectOverlappingRecord(before.userId(), id, request.checkInTime(), request.checkOutTime());
        LocalDate dutyDate = request.checkInTime().toLocalDate();
        int dutyWeekday = dutyDate.getDayOfWeek().getValue();
        EligibilitySnapshot eligibility = Boolean.TRUE.equals(request.recomputeSnapshot())
                ? currentEligibility(request.checkInTime())
                : new EligibilitySnapshot(
                before.dutyDay(), before.withinDutyPeriod(),
                before.requireDutyDay(), before.requireDutyPeriod()
        );
        String checkInStatus = current.role() == Role.MINISTER
                ? ReviewStatus.AUTO_APPROVED.name()
                : normalizeSubmittedReviewStatus(request.checkInStatus(), "签到审核状态");
        String checkOutStatus;
        if (request.checkOutTime() == null) {
            checkOutStatus = ReviewStatus.NOT_SUBMITTED.name();
        } else if (current.role() == Role.MINISTER) {
            checkOutStatus = ReviewStatus.AUTO_APPROVED.name();
        } else {
            checkOutStatus = normalizeSubmittedReviewStatus(request.checkOutStatus(), "签退审核状态");
        }
        records.manualUpdate(
                id, dutyDate, dutyWeekday, eligibility.dutyDay(), eligibility.withinDutyPeriod(),
                eligibility.requireDutyDay(), eligibility.requireDutyPeriod(),
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
        validateManualTimes(request.checkInTime(), request.checkOutTime());

        UserSummary user = users.findActiveByStudentNo(request.studentNo().trim())
                .orElseThrow(() -> ApiException.notFound("学号不存在或账号已停用"));
        requireWriteLock(records.acquireUserAttendanceWriteLock(user.id()));
        rejectOverlappingRecord(user.id(), null, request.checkInTime(), request.checkOutTime());
        LocalDate dutyDate = request.checkInTime().toLocalDate();
        int weekday = dutyDate.getDayOfWeek().getValue();
        EligibilitySnapshot eligibility = currentEligibility(request.checkInTime());
        String checkOutStatus = request.checkOutTime() == null
                ? ReviewStatus.NOT_SUBMITTED.name()
                : ReviewStatus.AUTO_APPROVED.name();
        long id = records.insertManual(
                user.id(), user.studentNo(), user.name(), dutyDate, weekday,
                eligibility.dutyDay(), eligibility.withinDutyPeriod(),
                eligibility.requireDutyDay(), eligibility.requireDutyPeriod(),
                Timestamp.valueOf(request.checkInTime()),
                request.checkOutTime() == null ? null : Timestamp.valueOf(request.checkOutTime()),
                ReviewStatus.AUTO_APPROVED.name(), checkOutStatus, request.reason().trim(), current.id());
        recompute(id);
        AttendanceRecord created = records.findById(id).orElseThrow();
        logs.log("MANUAL_CREATE_ATTENDANCE", "attendance_records", id, null, created, request.reason());
        return created;
    }

    private void validateManualTimes(LocalDateTime checkInTime, LocalDateTime checkOutTime) {
        LocalDateTime latestAllowed = LocalDateTime.now(clock).plusMinutes(MANUAL_TIME_FUTURE_TOLERANCE_MINUTES);
        if (checkInTime.isAfter(latestAllowed)) {
            throw ApiException.badRequest("签到时间不能是超过当前时间 5 分钟的未来时间");
        }
        if (checkOutTime != null && checkOutTime.isAfter(latestAllowed)) {
            throw ApiException.badRequest("签退时间不能是超过当前时间 5 分钟的未来时间");
        }
    }

    private EligibilitySnapshot currentEligibility(LocalDateTime checkInTime) {
        LocalDate dutyDate = checkInTime.toLocalDate();
        AttendancePolicyService.AttendancePolicy policy = policies.current();
        return new EligibilitySnapshot(
                weekdays.isDutyWeekday(dutyDate.getDayOfWeek().getValue()),
                periods.contains(checkInTime.toLocalTime()),
                policy.requireDutyDay(),
                policy.requireDutyPeriod()
        );
    }

    private void rejectOverlappingRecord(long userId, Long excludedRecordId,
                                         LocalDateTime checkInTime, LocalDateTime checkOutTime) {
        if (records.hasOverlappingRecord(
                userId,
                excludedRecordId,
                Timestamp.valueOf(checkInTime),
                checkOutTime == null ? null : Timestamp.valueOf(checkOutTime))) {
            throw ApiException.conflict("该成员已有时间重叠的签到记录，请调整时间后重试");
        }
    }

    private void requireWriteLock(int affectedRows) {
        if (affectedRows != 1) {
            throw ApiException.conflict("成员状态已变化，请刷新后重试");
        }
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
        LocalDate weekStart = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
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

    private String normalizeSubmittedReviewStatus(String status, String fieldName) {
        try {
            ReviewStatus normalized = ReviewStatus.valueOf(status == null ? "" : status.trim().toUpperCase());
            if (normalized == ReviewStatus.NOT_SUBMITTED) {
                String message = fieldName.startsWith("签到")
                        ? fieldName + "不能为未提交"
                        : "已有签退时间时，" + fieldName + "不能为未提交";
                throw ApiException.badRequest(message);
            }
            return normalized.name();
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
        if (hasFutureTime(record)) {
            records.updateEffective(id, 0, 0, "INVALID");
            return;
        }
        if ((record.requireDutyDay() && !record.dutyDay())
                || (record.requireDutyPeriod() && !record.withinDutyPeriod())) {
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
        if (duration.isNegative() || duration.isZero()) {
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

    private String submissionMessage(String action, boolean dutyDay, boolean withinDutyPeriod,
                                     boolean requireDutyDay, boolean requireDutyPeriod) {
        if (!dutyDay) {
            if (requireDutyDay) {
                return action + "已提交；今日不是值班日，按当前规则不计入有效时长";
            }
            return action + "已提交；今日不是值班日，是否计入有效时长由审核结果决定";
        }
        if (!withinDutyPeriod) {
            if (requireDutyPeriod) {
                return action + "已提交；当前不在值班时段，按当前规则不计入有效时长";
            }
            return action + "已提交；当前不在值班时段，是否计入有效时长由审核结果决定";
        }
        return action + "提交成功";
    }

    private String lookupMessage(boolean dutyDay, boolean withinDutyPeriod,
                                 AttendancePolicyService.AttendancePolicy policy,
                                 String normalMessage, String relaxedMessage) {
        if ((!dutyDay && policy.requireDutyDay())
                || (!withinDutyPeriod && policy.requireDutyPeriod())) {
            return "当前不符合有效时长规则，仍可提交，但本次不计入有效时长";
        }
        return dutyDay && withinDutyPeriod ? normalMessage : relaxedMessage;
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

    private String normalizeBulkScope(String scope) {
        String normalized = scope == null || scope.isBlank()
                ? "SELECTED"
                : scope.trim().toUpperCase();
        if (!normalized.equals("SELECTED") && !normalized.equals("ALL_PENDING")) {
            throw ApiException.badRequest("批量审核范围只能是 SELECTED 或 ALL_PENDING");
        }
        return normalized;
    }

    private Set<Long> validBulkIds(List<Long> ids) {
        if (ids == null) {
            return Set.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private List<AttendanceRecord> findBulkCandidates(Set<Long> ids) {
        List<Long> orderedIds = List.copyOf(ids);
        List<AttendanceRecord> candidates = new ArrayList<>(orderedIds.size());
        for (int start = 0; start < orderedIds.size(); start += BULK_REVIEW_BATCH_SIZE) {
            int end = Math.min(start + BULK_REVIEW_BATCH_SIZE, orderedIds.size());
            candidates.addAll(records.findAllByIds(orderedIds.subList(start, end)));
        }
        return candidates;
    }

    private void approveInBatches(List<Long> ids, long reviewerId, boolean checkIn) {
        for (int start = 0; start < ids.size(); start += BULK_REVIEW_BATCH_SIZE) {
            int end = Math.min(start + BULK_REVIEW_BATCH_SIZE, ids.size());
            List<Long> batch = ids.subList(start, end);
            int[] counts = checkIn
                    ? records.approveCheckIns(batch, reviewerId)
                    : records.approveCheckOuts(batch, reviewerId);
            requireCompleteBatch(counts, batch.size(), "批量审核时有记录状态发生变化");
        }
    }

    private void recomputeInBatches(List<Long> ids, long updatedBy) {
        for (int start = 0; start < ids.size(); start += BULK_REVIEW_BATCH_SIZE) {
            int end = Math.min(start + BULK_REVIEW_BATCH_SIZE, ids.size());
            List<AttendanceRecord> reviewedRecords = records.findAllByIds(ids.subList(start, end));
            if (reviewedRecords.size() != end - start) {
                throw ApiException.conflict("批量审核时有记录已不存在，操作已回滚");
            }
            List<AttendanceRepository.EffectiveUpdate> updates = reviewedRecords.stream()
                    .map(this::effectiveUpdate)
                    .toList();
            int[] counts = records.batchUpdateEffective(updates, updatedBy);
            requireCompleteBatch(counts, updates.size(), "批量审核时有效时长更新失败");
        }
    }

    private AttendanceRepository.EffectiveUpdate effectiveUpdate(AttendanceRecord record) {
        if (ReviewStatus.REJECTED.name().equals(record.checkInStatus())
                || ReviewStatus.REJECTED.name().equals(record.checkOutStatus())) {
            return new AttendanceRepository.EffectiveUpdate(record.id(), 0, 0, "INVALID");
        }
        if (hasFutureTime(record)) {
            return new AttendanceRepository.EffectiveUpdate(record.id(), 0, 0, "INVALID");
        }
        if ((record.requireDutyDay() && !record.dutyDay())
                || (record.requireDutyPeriod() && !record.withinDutyPeriod())) {
            return new AttendanceRepository.EffectiveUpdate(record.id(), 0, 0, "INVALID");
        }
        if (record.checkOutTime() == null || ReviewStatus.NOT_SUBMITTED.name().equals(record.checkOutStatus())) {
            return new AttendanceRepository.EffectiveUpdate(record.id(), 0, 0, "INCOMPLETE");
        }
        if (!approved(record.checkInStatus()) || !approved(record.checkOutStatus())) {
            return new AttendanceRepository.EffectiveUpdate(record.id(), 0, 0, "PENDING");
        }
        Duration duration = Duration.between(record.checkInTime(), record.checkOutTime());
        if (duration.isNegative() || duration.isZero()) {
            return new AttendanceRepository.EffectiveUpdate(record.id(), 0, 0, "INVALID");
        }
        long minutes = duration.toMinutes();
        return new AttendanceRepository.EffectiveUpdate(
                record.id(),
                (int) minutes,
                (int) ((minutes + 30) / 60),
                "VALID"
        );
    }

    private boolean hasFutureTime(AttendanceRecord record) {
        LocalDateTime latestAllowed = LocalDateTime.now(clock).plusMinutes(MANUAL_TIME_FUTURE_TOLERANCE_MINUTES);
        return record.checkInTime().isAfter(latestAllowed)
                || (record.checkOutTime() != null && record.checkOutTime().isAfter(latestAllowed));
    }

    private void requireCompleteBatch(int[] counts, int expected, String message) {
        if (counts.length != expected) {
            throw ApiException.conflict(message + "，操作已回滚");
        }
        for (int count : counts) {
            if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
                throw ApiException.conflict(message + "，操作已回滚");
            }
        }
    }

    private String normalizeEffectiveStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return EffectiveStatus.valueOf(status.trim().toUpperCase()).name();
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("有效状态不合法");
        }
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
            String reason,
            Boolean recomputeSnapshot
    ) {
        public ManualUpdateRequest(LocalDateTime checkInTime, LocalDateTime checkOutTime,
                                   String checkInStatus, String checkOutStatus, String reason) {
            this(checkInTime, checkOutTime, checkInStatus, checkOutStatus, reason, false);
        }
    }

    public record ManualCreateRequest(
            String studentNo,
            LocalDateTime checkInTime,
            LocalDateTime checkOutTime,
            String reason
    ) {
    }

    public record PendingReviewQueue(
            List<AttendanceRecord> items,
            long recordCount,
            long itemCount,
            boolean truncated
    ) {
    }

    public record BulkReviewRequest(List<Long> ids, String part, String scope) {
        public BulkReviewRequest(List<Long> ids, String part) {
            this(ids, part, "SELECTED");
        }
    }

    public record BulkReviewResult(int matched, int reviewed, int skipped, List<String> errors) {
    }

    private record EligibilitySnapshot(
            boolean dutyDay,
            boolean withinDutyPeriod,
            boolean requireDutyDay,
            boolean requireDutyPeriod
    ) {
    }
}
