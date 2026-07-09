package com.ca.attendance.maintenance;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {
    private final MaintenanceSummaryService summary;

    public MaintenanceController(MaintenanceSummaryService summary) {
        this.summary = summary;
    }

    @GetMapping("/summary")
    public MaintenanceSummaryService.MaintenanceSummary summary() {
        return summary.summary();
    }
}
