package com.ca.attendance.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings/duty-periods")
public class DutyPeriodController {
    private final DutyPeriodService service;

    public DutyPeriodController(DutyPeriodService service) {
        this.service = service;
    }

    @GetMapping
    public List<DutyPeriodItem> list() {
        service.requireManager();
        return service.list();
    }

    @PutMapping
    public List<DutyPeriodItem> update(@Valid @RequestBody UpdateDutyPeriodsRequest request) {
        return service.update(request.periods());
    }

    public record UpdateDutyPeriodsRequest(@NotNull List<DutyPeriodService.DutyPeriodRequest> periods) {
    }
}

@RestController
@RequestMapping("/api/public/duty-periods")
class PublicDutyPeriodController {
    private final DutyPeriodService service;

    PublicDutyPeriodController(DutyPeriodService service) {
        this.service = service;
    }

    @GetMapping
    public List<DutyPeriodItem> list() {
        return service.list();
    }
}
