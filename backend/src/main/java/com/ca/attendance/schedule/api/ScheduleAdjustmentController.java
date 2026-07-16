package com.ca.attendance.schedule.api;

import com.ca.attendance.schedule.application.EffectiveScheduleService;
import com.ca.attendance.schedule.application.ScheduleAdjustmentService;
import com.ca.attendance.schedule.domain.EffectiveScheduleDay;
import com.ca.attendance.schedule.domain.ScheduleException;
import com.ca.attendance.schedule.domain.ShiftReassignment;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleAdjustmentController {
    private final ScheduleAdjustmentService adjustments;
    private final EffectiveScheduleService effectiveSchedules;

    public ScheduleAdjustmentController(ScheduleAdjustmentService adjustments,
                                        EffectiveScheduleService effectiveSchedules) {
        this.adjustments = adjustments;
        this.effectiveSchedules = effectiveSchedules;
    }

    @GetMapping("/effective")
    public EffectiveScheduleDay effective(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long termId
    ) {
        return effectiveSchedules.day(date, termId);
    }

    @GetMapping("/effective/week")
    public List<EffectiveScheduleDay> effectiveWeek(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long termId
    ) {
        return effectiveSchedules.week(date, termId);
    }

    @GetMapping("/exceptions")
    public List<ScheduleException> exceptions(
            @RequestParam long termId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return adjustments.exceptions(termId, from, to);
    }

    @PostMapping("/exceptions")
    public ScheduleException createException(@RequestBody ScheduleAdjustmentService.ExceptionRequest request) {
        return adjustments.createException(request);
    }

    @PutMapping("/exceptions/{id}")
    public ScheduleException updateException(@PathVariable long id,
                                             @RequestBody ScheduleAdjustmentService.ExceptionRequest request) {
        return adjustments.updateException(id, request);
    }

    @DeleteMapping("/exceptions/{id}")
    public void deleteException(@PathVariable long id,
                                @RequestBody ScheduleAdjustmentService.ReasonRequest request) {
        adjustments.deleteException(id, request);
    }

    @GetMapping("/reassignments")
    public List<ShiftReassignment> reassignments(
            @RequestParam long termId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return adjustments.reassignments(termId, from, to);
    }

    @PostMapping("/reassignments")
    public ShiftReassignment createReassignment(
            @RequestBody ScheduleAdjustmentService.ReassignmentRequest request) {
        return adjustments.createReassignment(request);
    }

    @PutMapping("/reassignments/{id}")
    public ShiftReassignment updateReassignment(
            @PathVariable long id,
            @RequestBody ScheduleAdjustmentService.ReassignmentRequest request) {
        return adjustments.updateReassignment(id, request);
    }

    @DeleteMapping("/reassignments/{id}")
    public void deleteReassignment(@PathVariable long id,
                                   @RequestBody ScheduleAdjustmentService.ReasonRequest request) {
        adjustments.deleteReassignment(id, request);
    }
}

@RestController
@RequestMapping("/api/public/schedules/effective")
class PublicEffectiveScheduleController {
    private final EffectiveScheduleService effectiveSchedules;

    PublicEffectiveScheduleController(EffectiveScheduleService effectiveSchedules) {
        this.effectiveSchedules = effectiveSchedules;
    }

    @GetMapping("/today")
    public EffectiveScheduleDay today(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return effectiveSchedules.publicDay(date == null ? LocalDate.now() : date);
    }

    @GetMapping("/week")
    public List<EffectiveScheduleDay> week(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return effectiveSchedules.publicWeek(date == null ? LocalDate.now() : date);
    }
}
