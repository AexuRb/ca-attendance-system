package com.ca.attendance.schedule;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class DutyScheduleController {
    private final DutyScheduleService schedules;

    public DutyScheduleController(DutyScheduleService schedules) {
        this.schedules = schedules;
    }

    @GetMapping
    public List<DutyScheduleSlotItem> list() {
        return schedules.list();
    }

    @PostMapping
    public DutyScheduleSlotItem create(@RequestBody DutyScheduleService.SlotRequest request) {
        return schedules.create(request);
    }

    @PutMapping("/{id}")
    public DutyScheduleSlotItem update(@PathVariable long id, @RequestBody DutyScheduleService.SlotRequest request) {
        return schedules.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void archive(@PathVariable long id) {
        schedules.archive(id);
    }
}

@RestController
@RequestMapping("/api/public/schedules")
class PublicDutyScheduleController {
    private final DutyScheduleService schedules;

    PublicDutyScheduleController(DutyScheduleService schedules) {
        this.schedules = schedules;
    }

    @GetMapping("/today")
    public List<DutyScheduleSlotItem> today(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return schedules.today(date == null ? LocalDate.now() : date);
    }

    @GetMapping("/week")
    public List<DutyScheduleSlotItem> week(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return schedules.week(date == null ? LocalDate.now() : date);
    }
}
