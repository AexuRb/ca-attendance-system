package com.ca.attendance.settings;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/attendance-policy")
public class AttendancePolicyController {
    private final AttendancePolicyService policies;

    public AttendancePolicyController(AttendancePolicyService policies) {
        this.policies = policies;
    }

    @GetMapping
    public AttendancePolicyService.AttendancePolicy current() {
        return policies.readForManager();
    }

    @PutMapping
    public AttendancePolicyService.AttendancePolicy update(
            @Valid @RequestBody AttendancePolicyService.UpdateAttendancePolicyRequest request
    ) {
        return policies.update(request);
    }
}
