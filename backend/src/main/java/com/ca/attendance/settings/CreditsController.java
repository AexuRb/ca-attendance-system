package com.ca.attendance.settings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CreditsController {
    private final CreditsService credits;
    public CreditsController(CreditsService credits) { this.credits = credits; }
    @GetMapping("/api/settings/credits")
    public CreditsService.Credits current() { return credits.current(); }
    @PutMapping("/api/settings/credits")
    public CreditsService.Credits update(@RequestBody CreditsService.Update request) { return credits.update(request); }
}
