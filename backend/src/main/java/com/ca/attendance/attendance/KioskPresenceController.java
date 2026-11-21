package com.ca.attendance.attendance;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KioskPresenceController {
    private final KioskPresenceService presence;

    public KioskPresenceController(KioskPresenceService presence) {
        this.presence = presence;
    }

    @GetMapping("/api/public/attendance/present")
    public ResponseEntity<KioskPresenceService.Snapshot> present() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(presence.current());
    }
}
