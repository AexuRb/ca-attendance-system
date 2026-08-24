package com.ca.attendance.attendance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/attendance")
public class PublicAttendanceController {
    private final AttendanceService attendance;
    private final PublicAttendanceRequestGuard requestGuard;

    public PublicAttendanceController(AttendanceService attendance, PublicAttendanceRequestGuard requestGuard) {
        this.attendance = attendance;
        this.requestGuard = requestGuard;
    }

    @GetMapping("/lookup")
    public AttendanceService.PublicLookupResponse lookupByInput(@RequestParam String query,
                                                                 HttpServletRequest servletRequest) {
        requestGuard.requireLookup(servletRequest.getRemoteAddr());
        return attendance.lookupByInput(query);
    }

    @PostMapping("/submit")
    public AttendanceService.SubmitResponse submit(@Valid @RequestBody SubmitRequest request,
                                                    HttpServletRequest servletRequest) {
        requestGuard.requireSubmission(servletRequest.getRemoteAddr());
        return attendance.submitPublicSelection(request.memberToken(), request.requestId());
    }

    public record SubmitRequest(@NotBlank String memberToken, @NotBlank String requestId) {
    }
}
