package com.ca.attendance.schedule.application;

import com.ca.attendance.common.ApiException;
import com.ca.attendance.schedule.domain.EffectiveScheduleDay;
import com.ca.attendance.schedule.domain.ScheduleAdjustmentType;
import com.ca.attendance.schedule.domain.ScheduleAssignee;
import com.ca.attendance.schedule.domain.ScheduleException;
import com.ca.attendance.schedule.domain.ShiftReassignment;
import com.ca.attendance.schedule.infrastructure.ScheduleAdjustmentRepository;
import com.ca.attendance.term.domain.AcademicTerm;
import com.ca.attendance.term.domain.TermStatus;
import com.ca.attendance.term.infrastructure.AcademicTermRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class EffectiveScheduleService {
    private final AcademicTermRepository terms;
    private final ScheduleAdjustmentRepository schedules;

    public EffectiveScheduleService(AcademicTermRepository terms, ScheduleAdjustmentRepository schedules) {
        this.terms = terms;
        this.schedules = schedules;
    }

    public EffectiveScheduleDay publicDay(LocalDate date) {
        return terms.active().map(term -> resolve(term, date)).orElseGet(() -> emptyDay(date));
    }

    public EffectiveScheduleDay day(LocalDate date, Long termId) {
        AcademicTerm term = termId == null
                ? terms.active().orElseThrow(() -> ApiException.conflict("当前没有活动学期"))
                : terms.find(termId).orElseThrow(() -> ApiException.notFound("学期不存在"));
        return resolve(term, date);
    }

    public List<EffectiveScheduleDay> publicWeek(LocalDate date) {
        return terms.active().map(term -> week(term, date)).orElseGet(() -> emptyWeek(date));
    }

    public List<EffectiveScheduleDay> week(LocalDate date, Long termId) {
        AcademicTerm term = termId == null
                ? terms.active().orElseThrow(() -> ApiException.conflict("当前没有活动学期"))
                : terms.find(termId).orElseThrow(() -> ApiException.notFound("学期不存在"));
        return week(term, date);
    }

    private List<EffectiveScheduleDay> week(AcademicTerm term, LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<EffectiveScheduleDay> result = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            LocalDate day = monday.plusDays(index);
            if (!day.isBefore(term.startDate()) && !day.isAfter(term.endDate())) {
                result.add(resolve(term, day));
            }
        }
        return List.copyOf(result);
    }

    private List<EffectiveScheduleDay> emptyWeek(LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<EffectiveScheduleDay> result = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            result.add(emptyDay(monday.plusDays(index)));
        }
        return List.copyOf(result);
    }

    private EffectiveScheduleDay emptyDay(LocalDate date) {
        int weekday = date.getDayOfWeek().getValue();
        return new EffectiveScheduleDay(0, "未设置活动学期", date, weekday, weekdayName(weekday), false, List.of());
    }

    private EffectiveScheduleDay resolve(AcademicTerm term, LocalDate date) {
        if (term.status() == TermStatus.DRAFT) {
            throw ApiException.conflict("草稿学期没有生效排班");
        }
        if (!term.contains(date)) {
            throw ApiException.badRequest("所选日期不在该学期范围内");
        }

        int weekday = date.getDayOfWeek().getValue();
        List<EffectiveScheduleDay.EffectiveSlot> slots = new ArrayList<>(schedules.baseSlots(term.id(), weekday));
        List<ScheduleException> exceptions = schedules.exceptions(term.id(), date, date);
        boolean cancelled = exceptions.stream().anyMatch(item -> item.type() == ScheduleAdjustmentType.DAY_CANCELLED);
        if (cancelled) {
            slots.clear();
        } else {
            for (ScheduleException exception : exceptions) {
                applyException(slots, exception);
            }
            for (ShiftReassignment reassignment : schedules.reassignments(term.id(), date, date)) {
                applyReassignment(slots, reassignment);
            }
        }
        slots.sort(Comparator
                .comparing(EffectiveScheduleDay.EffectiveSlot::startTime,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EffectiveScheduleDay.EffectiveSlot::endTime,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EffectiveScheduleDay.EffectiveSlot::key));
        return new EffectiveScheduleDay(
                term.id(), term.name(), date, weekday, weekdayName(weekday), cancelled, List.copyOf(slots)
        );
    }

    private void applyException(List<EffectiveScheduleDay.EffectiveSlot> slots, ScheduleException exception) {
        switch (exception.type()) {
            case DAY_CANCELLED -> slots.clear();
            case PERIOD_CANCELLED -> slots.removeIf(slot -> matches(slot, exception.sourceSlotId(),
                    exception.startTime(), exception.endTime()));
            case TEMPORARY_ADDITION -> slots.add(new EffectiveScheduleDay.EffectiveSlot(
                    "exception-" + exception.id(), null, exception.id(),
                    exception.startTime(), exception.endTime(), exception.title(), exception.location(),
                    exception.reason(), "TEMPORARY_ADDITION", exception.assignees()
            ));
            case ASSIGNEE_OVERRIDE -> {
                for (int index = 0; index < slots.size(); index++) {
                    EffectiveScheduleDay.EffectiveSlot slot = slots.get(index);
                    if (matches(slot, exception.sourceSlotId(), exception.startTime(), exception.endTime())) {
                        slots.set(index, slot.withAssignees(exception.assignees()));
                    }
                }
            }
        }
    }

    private void applyReassignment(List<EffectiveScheduleDay.EffectiveSlot> slots, ShiftReassignment reassignment) {
        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            EffectiveScheduleDay.EffectiveSlot slot = slots.get(slotIndex);
            if (!matches(slot, reassignment.sourceSlotId(), reassignment.startTime(), reassignment.endTime())) {
                continue;
            }
            List<ScheduleAssignee> assignees = new ArrayList<>(slot.assignees());
            for (int assigneeIndex = 0; assigneeIndex < assignees.size(); assigneeIndex++) {
                ScheduleAssignee assignee = assignees.get(assigneeIndex);
                if (sameAssignee(assignee, reassignment.original())) {
                    assignees.set(assigneeIndex, assignee.asReplacement(reassignment.replacement()));
                }
            }
            slots.set(slotIndex, slot.withAssignees(assignees));
        }
    }

    private boolean matches(EffectiveScheduleDay.EffectiveSlot slot, Long sourceSlotId,
                            LocalTime start, LocalTime end) {
        if (sourceSlotId != null) {
            return sourceSlotId.equals(slot.sourceSlotId());
        }
        return start != null && end != null && start.equals(slot.startTime()) && end.equals(slot.endTime());
    }

    private boolean sameAssignee(ScheduleAssignee left, ScheduleAssignee right) {
        if (left.userId() != null && right.userId() != null) {
            return left.userId().equals(right.userId());
        }
        return left.studentNo() != null && left.studentNo().equals(right.studentNo());
    }

    private String weekdayName(int weekday) {
        return switch (weekday) {
            case 1 -> "星期一";
            case 2 -> "星期二";
            case 3 -> "星期三";
            case 4 -> "星期四";
            case 5 -> "星期五";
            case 6 -> "星期六";
            case 7 -> "星期日";
            default -> "未知";
        };
    }
}
