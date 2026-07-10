package com.ca.attendance.training;

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
import java.util.Map;

@RestController
@RequestMapping("/api/trainings")
public class TrainingController {
    private final TrainingService trainings;

    public TrainingController(TrainingService trainings) {
        this.trainings = trainings;
    }

    @GetMapping
    public List<TrainingSessionItem> list(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return trainings.list(keyword, status, from, to);
    }

    @PostMapping
    public TrainingSessionItem create(@RequestBody TrainingService.SessionRequest request) {
        return trainings.create(request);
    }

    @PutMapping("/{id}")
    public TrainingSessionItem update(@PathVariable long id, @RequestBody TrainingService.SessionRequest request) {
        return trainings.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void archive(@PathVariable long id) {
        trainings.archive(id);
    }

    @GetMapping("/me/hours")
    public Map<String, Object> myHours(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return trainings.myHours(from, to);
    }

    @GetMapping("/{id}/participants")
    public List<TrainingParticipantItem> participants(@PathVariable long id) {
        return trainings.participants(id);
    }

    @PostMapping("/{id}/participants")
    public TrainingParticipantItem addParticipant(@PathVariable long id, @RequestBody TrainingService.ParticipantRequest request) {
        return trainings.addParticipant(id, request);
    }

    @PutMapping("/{id}/participants/{participantId}")
    public TrainingParticipantItem updateParticipant(@PathVariable long id,
                                                     @PathVariable long participantId,
                                                     @RequestBody TrainingService.ParticipantRequest request) {
        return trainings.updateParticipant(id, participantId, request);
    }

    @DeleteMapping("/{id}/participants/{participantId}")
    public void deleteParticipant(@PathVariable long id, @PathVariable long participantId) {
        trainings.deleteParticipant(id, participantId);
    }

    @PostMapping(value = "/{id}/participants/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TrainingService.ImportResult importParticipants(@PathVariable long id, @RequestParam("file") MultipartFile file) {
        return trainings.importParticipants(id, file);
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> importTemplate() {
        TrainingService.ExportFile file = trainings.exportImportTemplate();
        return excel(file.filename(), file.bytes());
    }

    @GetMapping("/{id}/participants/import-template")
    public ResponseEntity<byte[]> sessionImportTemplate(@PathVariable long id) {
        TrainingService.ExportFile file = trainings.exportSessionImportTemplate(id);
        return excel(file.filename(), file.bytes());
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportSession(@PathVariable long id) {
        TrainingService.ExportFile file = trainings.exportSession(id);
        return excel(file.filename(), file.bytes());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportSummary(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        TrainingService.ExportFile file = trainings.exportSummary(keyword, status, from, to);
        return excel(file.filename(), file.bytes());
    }

    private ResponseEntity<byte[]> excel(String filename, byte[] bytes) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
