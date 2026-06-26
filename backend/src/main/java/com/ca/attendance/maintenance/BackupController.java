package com.ca.attendance.maintenance;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/maintenance/backups")
public class BackupController {
    private final BackupService backups;

    public BackupController(BackupService backups) {
        this.backups = backups;
    }

    @GetMapping
    public List<BackupService.BackupItem> list() {
        return backups.list();
    }

    @PostMapping
    public BackupService.BackupItem create() {
        return backups.create();
    }

    @GetMapping("/{filename}")
    public ResponseEntity<byte[]> download(@PathVariable String filename) {
        BackupService.BackupFile file = backups.download(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(file.bytes());
    }

    @DeleteMapping("/{filename}")
    public void delete(@PathVariable String filename) {
        backups.delete(filename);
    }
}
