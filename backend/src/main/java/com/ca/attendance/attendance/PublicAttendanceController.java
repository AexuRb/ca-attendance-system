package com.ca.attendance.attendance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/attendance")
public class PublicAttendanceController {
    private final AttendanceService attendance;

    public PublicAttendanceController(AttendanceService attendance) {
        this.attendance = attendance;
    }

    @GetMapping("/lookup/{studentNo}")
    public AttendanceService.PublicLookupResponse lookup(@PathVariable String studentNo) {
        return attendance.lookup(studentNo);
    }

    @GetMapping("/lookup")
    public AttendanceService.PublicLookupResponse lookupByInput(@RequestParam String query) {
        return attendance.lookupByInput(query);
    }

    @PostMapping("/submit")
    public AttendanceService.SubmitResponse submit(@Valid @RequestBody SubmitRequest request) {
        return attendance.submitPublic(request.studentNo(), request.requestId());
    }

    public record SubmitRequest(@NotBlank String studentNo, String requestId) {
    }
}
