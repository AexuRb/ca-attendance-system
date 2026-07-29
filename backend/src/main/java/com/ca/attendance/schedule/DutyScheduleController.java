package com.ca.attendance.schedule;

import com.ca.attendance.schedule.application.FixedScheduleCalendarService;
import com.ca.attendance.schedule.domain.FixedScheduleDay;
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

    @GetMapping("/assignee-candidates")
    public List<DutyScheduleService.AssigneeCandidate> assigneeCandidates(
            @RequestParam(required = false) String keyword
    ) {
        return schedules.assigneeCandidates(keyword);
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
    private final FixedScheduleCalendarService calendar;

    PublicDutyScheduleController(FixedScheduleCalendarService calendar) {
        this.calendar = calendar;
    }

    @GetMapping("/today")
    public FixedScheduleDay today(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return calendar.day(date == null ? LocalDate.now() : date);
    }

    @GetMapping("/week")
    public List<FixedScheduleDay> week(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return calendar.week(date == null ? LocalDate.now() : date);
    }
}
