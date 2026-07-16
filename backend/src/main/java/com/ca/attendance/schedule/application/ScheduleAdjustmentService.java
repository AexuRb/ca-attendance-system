package com.ca.attendance.schedule.application;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import com.ca.attendance.schedule.domain.EffectiveScheduleDay;
import com.ca.attendance.schedule.domain.ScheduleAdjustmentType;
import com.ca.attendance.schedule.domain.ScheduleAssignee;
import com.ca.attendance.schedule.domain.ScheduleException;
import com.ca.attendance.schedule.domain.ShiftReassignment;
import com.ca.attendance.schedule.infrastructure.ScheduleAdjustmentRepository;
import com.ca.attendance.schedule.infrastructure.ScheduleAdjustmentRepository.UserRef;
import com.ca.attendance.settings.DutyPeriodItem;
import com.ca.attendance.settings.DutyPeriodService;
import com.ca.attendance.shared.application.AuditLogPort;
import com.ca.attendance.shared.application.CurrentActor;
import com.ca.attendance.term.application.TermWritePolicy;
import com.ca.attendance.term.domain.AcademicTerm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ScheduleAdjustmentService {
    private static final Set<String> SCHEDULE_ROLES = Set.of("MINISTER", "PRESIDENT", "ADMIN");

    private final ScheduleAdjustmentRepository schedules;
    private final EffectiveScheduleService effectiveSchedules;
    private final DutyPeriodService dutyPeriods;
    private final TermWritePolicy termPolicy;
    private final CurrentActor currentActor;
    private final AuditLogPort auditLogs;

    public ScheduleAdjustmentService(ScheduleAdjustmentRepository schedules,
                                     EffectiveScheduleService effectiveSchedules,
                                     DutyPeriodService dutyPeriods,
                                     TermWritePolicy termPolicy,
                                     CurrentActor currentActor,
                                     AuditLogPort auditLogs) {
        this.schedules = schedules;
        this.effectiveSchedules = effectiveSchedules;
        this.dutyPeriods = dutyPeriods;
        this.termPolicy = termPolicy;
        this.currentActor = currentActor;
        this.auditLogs = auditLogs;
    }

    public List<ScheduleException> exceptions(long termId, LocalDate from, LocalDate to) {
        requireManager();
        DateRange range = dateRange(from, to);
        return schedules.exceptions(termId, range.from(), range.to());
    }

    @Transactional
    public ScheduleException createException(ExceptionRequest request) {
        CurrentActor.Actor actor = requireManager();
        ExceptionValues values = exceptionValues(request, null, actor);
        long id = schedules.insertException(
                values.term().id(), values.date(), values.type(), values.sourceSlotId(),
                values.startTime(), values.endTime(), values.title(), values.location(), values.reason(),
                actor.id(), values.assignees());
        ScheduleException created = requireException(id);
        auditLogs.write("CREATE_SCHEDULE_EXCEPTION", "duty_schedule_exceptions", id,
                null, created, created.reason());
        return created;
    }

    @Transactional
    public ScheduleException updateException(long id, ExceptionRequest request) {
        CurrentActor.Actor actor = requireManager();
        ScheduleException before = requireException(id);
        termPolicy.requireScheduleWriteTerm(before.termId(), actor.role());
        ExceptionValues values = exceptionValues(request, id, actor);
        schedules.updateException(
                id, values.term().id(), values.date(), values.type(), values.sourceSlotId(),
                values.startTime(), values.endTime(), values.title(), values.location(), values.reason(),
                actor.id(), values.assignees());
        ScheduleException after = requireException(id);
        auditLogs.write("UPDATE_SCHEDULE_EXCEPTION", "duty_schedule_exceptions", id,
                before, after, after.reason());
        return after;
    }

    public void deleteException(long id, ReasonRequest request) {
        CurrentActor.Actor actor = requireManager();
        ScheduleException before = requireException(id);
        termPolicy.requireScheduleWriteTerm(before.termId(), actor.role());
        String reason = required(request == null ? null : request.reason(), "删除例外必须填写原因", 500);
        schedules.deleteException(id);
        auditLogs.write("DELETE_SCHEDULE_EXCEPTION", "duty_schedule_exceptions", id,
                before, null, reason);
    }

    public List<ShiftReassignment> reassignments(long termId, LocalDate from, LocalDate to) {
        requireManager();
        DateRange range = dateRange(from, to);
        return schedules.reassignments(termId, range.from(), range.to());
    }

    public ShiftReassignment createReassignment(ReassignmentRequest request) {
        CurrentActor.Actor actor = requireManager();
        ReassignmentValues values = reassignmentValues(request, null, null, actor);
        long id = schedules.insertReassignment(
                values.term().id(), values.date(), values.sourceSlotId(), values.startTime(), values.endTime(),
                values.original(), values.replacement(), values.reason(), actor.id());
        ShiftReassignment created = requireReassignment(id);
        auditLogs.write("CREATE_SHIFT_REASSIGNMENT", "duty_shift_reassignments", id,
                null, created, created.reason());
        return created;
    }

    public ShiftReassignment updateReassignment(long id, ReassignmentRequest request) {
        CurrentActor.Actor actor = requireManager();
        ShiftReassignment before = requireReassignment(id);
        termPolicy.requireScheduleWriteTerm(before.termId(), actor.role());
        ReassignmentValues values = reassignmentValues(request, id, before, actor);
        schedules.updateReassignment(
                id, values.term().id(), values.date(), values.sourceSlotId(), values.startTime(), values.endTime(),
                values.original(), values.replacement(), values.reason(), actor.id());
        ShiftReassignment after = requireReassignment(id);
        auditLogs.write("UPDATE_SHIFT_REASSIGNMENT", "duty_shift_reassignments", id,
                before, after, after.reason());
        return after;
    }

    public void deleteReassignment(long id, ReasonRequest request) {
        CurrentActor.Actor actor = requireManager();
        ShiftReassignment before = requireReassignment(id);
        termPolicy.requireScheduleWriteTerm(before.termId(), actor.role());
        String reason = required(request == null ? null : request.reason(), "删除调班必须填写原因", 500);
        schedules.deleteReassignment(id);
        auditLogs.write("DELETE_SHIFT_REASSIGNMENT", "duty_shift_reassignments", id,
                before, null, reason);
    }

    private ExceptionValues exceptionValues(ExceptionRequest request, Long excludedId, CurrentActor.Actor actor) {
        if (request == null || request.termId() == null || request.date() == null || request.type() == null) {
            throw ApiException.badRequest("请填写完整的排班例外信息");
        }
        AcademicTerm term = termPolicy.requireScheduleWriteTerm(request.termId(), actor.role());
        requireDateInTerm(term, request.date());
        String reason = required(request.reason(), "排班例外必须填写原因", 500);

        if (request.type() == ScheduleAdjustmentType.DAY_CANCELLED) {
            if (schedules.hasOtherExceptions(term.id(), request.date(), excludedId)) {
                throw ApiException.conflict("该日期已有时段例外，不能再设置全天取消");
            }
            return new ExceptionValues(term, request.date(), request.type(), null,
                    null, null, null, null, reason, List.of());
        }
        if (schedules.hasDayCancellation(term.id(), request.date(), excludedId)) {
            throw ApiException.conflict("该日期已设置全天取消");
        }

        LocalTime start = request.startTime();
        LocalTime end = request.endTime();
        EffectiveScheduleDay.EffectiveSlot source = null;
        if (request.sourceSlotId() != null) {
            source = schedules.baseSlots(term.id(), request.date().getDayOfWeek().getValue()).stream()
                    .filter(slot -> request.sourceSlotId().equals(slot.sourceSlotId()))
                    .findFirst().orElseThrow(() -> ApiException.badRequest("所选固定排班不属于该日期"));
            start = source.startTime();
            end = source.endTime();
        }
        requireConfiguredPeriod(start, end);

        List<ScheduleAssignee> assignees = switch (request.type()) {
            case TEMPORARY_ADDITION, ASSIGNEE_OVERRIDE -> normalizeAssignees(request.assignees());
            default -> List.of();
        };
        if ((request.type() == ScheduleAdjustmentType.TEMPORARY_ADDITION
                || request.type() == ScheduleAdjustmentType.ASSIGNEE_OVERRIDE) && assignees.isEmpty()) {
            throw ApiException.badRequest("请至少选择一位排班人员");
        }
        if (request.type() == ScheduleAdjustmentType.ASSIGNEE_OVERRIDE && source == null) {
            throw ApiException.badRequest("人员覆盖必须选择一个固定排班时段");
        }
        String title = request.type() == ScheduleAdjustmentType.TEMPORARY_ADDITION
                ? optional(request.title(), "临时值班", 100)
                : source == null ? null : source.title();
        String location = optional(request.location(), source == null ? null : source.location(), 120);
        return new ExceptionValues(term, request.date(), request.type(), request.sourceSlotId(),
                start, end, title, location, reason, assignees);
    }

    private ReassignmentValues reassignmentValues(ReassignmentRequest request, Long excludedId,
                                                  ShiftReassignment existing, CurrentActor.Actor actor) {
        if (request == null || request.termId() == null || request.date() == null) {
            throw ApiException.badRequest("请填写完整的调班信息");
        }
        AcademicTerm term = termPolicy.requireScheduleWriteTerm(request.termId(), actor.role());
        requireDateInTerm(term, request.date());
        String reason = required(request.reason(), "调班必须填写原因", 500);
        UserRef original = requireScheduleUser(request.originalStudentNo(), "原值班人员不存在或已停用");
        UserRef replacement = requireScheduleUser(request.replacementStudentNo(), "替班人员不存在或已停用");
        if (original.id() == replacement.id()) {
            throw ApiException.badRequest("原值班人员和替班人员不能相同");
        }

        EffectiveScheduleDay day = effectiveSchedules.day(request.date(), term.id());
        EffectiveScheduleDay.EffectiveSlot slot = day.slots().stream()
                .filter(item -> slotMatches(item, request.sourceSlotId(), request.startTime(), request.endTime()))
                .findFirst().orElseThrow(() -> ApiException.badRequest("没有找到要调整的生效排班时段"));
        boolean originalScheduled = slot.assignees().stream()
                .anyMatch(item -> original.studentNo().equals(item.studentNo()));
        boolean sameExistingOriginal = existing != null
                && original.studentNo().equals(existing.original().studentNo())
                && existing.date().equals(request.date())
                && existing.startTime().equals(slot.startTime())
                && existing.endTime().equals(slot.endTime());
        if (!originalScheduled && !sameExistingOriginal) {
            throw ApiException.badRequest("原值班人员不在该时段的生效排班中");
        }
        if (schedules.reassignmentExists(term.id(), request.date(), slot.startTime(), slot.endTime(),
                original.id(), excludedId)) {
            throw ApiException.conflict("该人员在此时段已有调班记录");
        }
        return new ReassignmentValues(term, request.date(), slot.sourceSlotId(), slot.startTime(), slot.endTime(),
                original, replacement, reason);
    }

    private boolean slotMatches(EffectiveScheduleDay.EffectiveSlot slot, Long sourceSlotId,
                                LocalTime start, LocalTime end) {
        if (sourceSlotId != null) {
            return sourceSlotId.equals(slot.sourceSlotId());
        }
        return start != null && end != null && start.equals(slot.startTime()) && end.equals(slot.endTime());
    }

    private List<ScheduleAssignee> normalizeAssignees(List<AssigneeRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        List<ScheduleAssignee> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (AssigneeRequest request : requests) {
            UserRef user = requireScheduleUser(request == null ? null : request.studentNo(),
                    "排班人员不存在或已停用");
            if (seen.add(user.id())) {
                result.add(user.asAssignee(result.size()));
            }
        }
        return List.copyOf(result);
    }

    private UserRef requireScheduleUser(String studentNo, String message) {
        String value = required(studentNo, "学号不能为空", 32);
        UserRef user = schedules.activeUser(value).orElseThrow(() -> ApiException.notFound(message));
        if (!SCHEDULE_ROLES.contains(user.role())) {
            throw ApiException.badRequest("排班人员必须是部长、会长或管理员");
        }
        return user;
    }

    private void requireConfiguredPeriod(LocalTime start, LocalTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw ApiException.badRequest("请选择有效的值班时段");
        }
        boolean configured = dutyPeriods.list().stream().anyMatch(period ->
                start.equals(LocalTime.parse(period.startTime())) && end.equals(LocalTime.parse(period.endTime())));
        if (!configured) {
            throw ApiException.badRequest("排班例外和调班只能使用设置中已有的值班时段");
        }
    }

    private CurrentActor.Actor requireManager() {
        CurrentActor.Actor actor = currentActor.require();
        if (actor.role() != Role.PRESIDENT && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("只有会长或管理员可以管理排班例外和调班");
        }
        return actor;
    }

    private ScheduleException requireException(long id) {
        return schedules.exception(id).orElseThrow(() -> ApiException.notFound("排班例外不存在"));
    }

    private ShiftReassignment requireReassignment(long id) {
        return schedules.reassignment(id).orElseThrow(() -> ApiException.notFound("调班记录不存在"));
    }

    private void requireDateInTerm(AcademicTerm term, LocalDate date) {
        if (!term.contains(date)) {
            throw ApiException.badRequest("所选日期不在该学期范围内");
        }
    }

    private DateRange dateRange(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().minusMonths(1) : from;
        LocalDate end = to == null ? LocalDate.now().plusMonths(3) : to;
        if (start.isAfter(end)) {
            throw ApiException.badRequest("开始日期不能晚于结束日期");
        }
        return new DateRange(start, end);
    }

    private String required(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(message);
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String optional(String value, String fallback, int maxLength) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    public record ExceptionRequest(
            Long termId, LocalDate date, ScheduleAdjustmentType type, Long sourceSlotId,
            LocalTime startTime, LocalTime endTime, String title, String location,
            String reason, List<AssigneeRequest> assignees
    ) {
    }

    public record AssigneeRequest(String studentNo) {
    }

    public record ReassignmentRequest(
            Long termId, LocalDate date, Long sourceSlotId, LocalTime startTime, LocalTime endTime,
            String originalStudentNo, String replacementStudentNo, String reason
    ) {
    }

    public record ReasonRequest(String reason) {
    }

    private record ExceptionValues(
            AcademicTerm term, LocalDate date, ScheduleAdjustmentType type, Long sourceSlotId,
            LocalTime startTime, LocalTime endTime, String title, String location,
            String reason, List<ScheduleAssignee> assignees
    ) {
    }

    private record ReassignmentValues(
            AcademicTerm term, LocalDate date, Long sourceSlotId, LocalTime startTime, LocalTime endTime,
            UserRef original, UserRef replacement, String reason
    ) {
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
