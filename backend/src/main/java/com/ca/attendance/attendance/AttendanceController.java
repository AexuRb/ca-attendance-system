package com.ca.attendance.attendance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {
    private final AttendanceService attendance;

    public AttendanceController(AttendanceService attendance) {
        this.attendance = attendance;
    }

    @GetMapping
    public List<AttendanceRecord> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String studentNo,
            @RequestParam(required = false) String status
    ) {
        return attendance.search(from, to, studentNo, status);
    }

    @GetMapping("/me")
    public List<AttendanceRecord> mine(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return attendance.myRecords(from, to);
    }

    @GetMapping("/reviews/pending")
    public List<AttendanceRecord> pending() {
        return attendance.pending();
    }

    @PostMapping("/{id}/review")
    public void review(@PathVariable long id, @Valid @RequestBody ReviewRequest request) {
        attendance.review(id, request.part(), request.action(), request.reason());
    }

    @PutMapping("/{id}/manual")
    public AttendanceRecord manualUpdate(@PathVariable long id, @Valid @RequestBody AttendanceService.ManualUpdateRequest request) {
        return attendance.manualUpdate(id, request);
    }

    public record ReviewRequest(@NotBlank String part, @NotBlank String action, String reason) {
    }
}
