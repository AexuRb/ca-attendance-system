package com.ca.attendance.settings;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppearanceController {
    private final AppearanceService appearances;

    public AppearanceController(AppearanceService appearances) {
        this.appearances = appearances;
    }

    @GetMapping("/api/public/appearance")
    public AppearanceService.AppearanceSetting current() {
        return appearances.current();
    }

    @PutMapping("/api/settings/appearance")
    public AppearanceService.AppearanceSetting update(
            @Valid @RequestBody AppearanceService.UpdateAppearanceRequest request
    ) {
        return appearances.update(request);
    }
}
