package com.ca.attendance.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings/weekdays")
public class DutyWeekdayController {
    private final DutyWeekdayService service;

    public DutyWeekdayController(DutyWeekdayService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        service.requireManager();
        return service.list();
    }

    @PutMapping
    public void update(@Valid @RequestBody UpdateWeekdaysRequest request) {
        service.update(request.enabledWeekdays());
    }

    public record UpdateWeekdaysRequest(@NotNull List<Integer> enabledWeekdays) {
    }
}
