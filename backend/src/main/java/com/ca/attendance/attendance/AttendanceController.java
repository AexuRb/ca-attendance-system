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

    @GetMapping("/open")
    public List<AttendanceRecord> open(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return attendance.openRecords(from, to);
    }

    @PostMapping("/{id}/review")
    public void review(@PathVariable long id, @Valid @RequestBody ReviewRequest request) {
        attendance.review(id, request.part(), request.action(), request.reason());
    }

    @PostMapping("/reviews/bulk")
    public AttendanceService.BulkReviewResult bulkReview(@Valid @RequestBody AttendanceService.BulkReviewRequest request) {
        return attendance.bulkReview(request);
    }

    @PostMapping("/manual")
    public AttendanceRecord manualCreate(@Valid @RequestBody AttendanceService.ManualCreateRequest request) {
        return attendance.manualCreate(request);
    }

    @PutMapping("/{id}/manual")
    public AttendanceRecord manualUpdate(@PathVariable long id, @Valid @RequestBody AttendanceService.ManualUpdateRequest request) {
        return attendance.manualUpdate(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        attendance.delete(id);
    }

    public record ReviewRequest(@NotBlank String part, @NotBlank String action, String reason) {
    }
}
