package com.ca.attendance.user;

import com.ca.attendance.attendance.KioskPresenceService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/skill-assessments")
public class MinisterSkillAssessmentController {
    private final UserService users;
    private final KioskPresenceService presence;
    private final Clock clock;

    public MinisterSkillAssessmentController(UserService users, KioskPresenceService presence, Clock clock) {
        this.users = users;
        this.presence = presence;
        this.clock = clock;
    }

    @GetMapping("/today")
    public ResponseEntity<TodayMembers> today() {
        LocalDate date = LocalDate.now(clock);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new TodayMembers(date, users.todayAssessmentMembers(date)));
    }

    @GetMapping("/overview")
    public ResponseEntity<KioskPresenceService.OverviewRoster> overview() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(presence.currentOverview());
    }

    @PatchMapping("/{id}")
    public UserService.AssessmentMember update(@PathVariable long id,
                                               @RequestBody UserService.TodayAssessmentRequest request) {
        return users.updateTodayAssessments(id, LocalDate.now(clock), request);
    }

    public record TodayMembers(LocalDate date, List<UserService.AssessmentMember> items) {
    }
}
