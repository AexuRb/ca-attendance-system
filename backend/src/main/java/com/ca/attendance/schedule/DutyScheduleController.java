package com.ca.attendance.schedule;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class DutyScheduleController {
    private final DutyScheduleService schedules;
    private final DutyScheduleImportService scheduleImports;

    public DutyScheduleController(DutyScheduleService schedules, DutyScheduleImportService scheduleImports) {
        this.schedules = schedules;
        this.scheduleImports = scheduleImports;
    }

    @GetMapping
    public List<DutyScheduleSlotItem> list() {
        return schedules.list();
    }

    @PostMapping
    public DutyScheduleSlotItem create(@RequestBody DutyScheduleService.SlotRequest request) {
        return schedules.create(request);
    }

    @PutMapping("/{id}")
    public DutyScheduleSlotItem update(@PathVariable long id, @RequestBody DutyScheduleService.SlotRequest request) {
        return schedules.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void archive(@PathVariable long id) {
        schedules.archive(id);
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> importTemplate() {
        DutyScheduleImportService.ExportFile file = scheduleImports.exportTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.bytes());
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DutyScheduleImportService.ImportPreview previewImport(@RequestParam("file") MultipartFile file) {
        return scheduleImports.preview(file);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DutyScheduleImportService.ImportResult importSchedules(@RequestParam("file") MultipartFile file) {
        return scheduleImports.importSchedules(file);
    }
}

@RestController
@RequestMapping("/api/public/schedules")
class PublicDutyScheduleController {
    private final DutyScheduleService schedules;

    PublicDutyScheduleController(DutyScheduleService schedules) {
        this.schedules = schedules;
    }

    @GetMapping("/today")
    public List<DutyScheduleSlotItem> today(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return schedules.today(date == null ? LocalDate.now() : date);
    }

    @GetMapping("/week")
    public List<DutyScheduleSlotItem> week(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return schedules.week(date == null ? LocalDate.now() : date);
    }
}
