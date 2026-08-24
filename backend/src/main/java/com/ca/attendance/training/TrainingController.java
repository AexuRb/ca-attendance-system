package com.ca.attendance.training;

import com.ca.attendance.common.PaginationPolicy;
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
@RequestMapping("/api/trainings")
public class TrainingController {
    private final TrainingService trainings;

    public TrainingController(TrainingService trainings) {
        this.trainings = trainings;
    }

    @GetMapping("/page")
    public TrainingService.TrainingSessionPage page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = PaginationPolicy.DEFAULT_PAGE_SIZE_TEXT) int pageSize
    ) {
        return trainings.page(keyword, status, from, to, page, pageSize);
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

    @GetMapping("/me")
    public List<MyTrainingRecordItem> myRecords(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return trainings.myRecords(from, to);
    }

    @GetMapping("/{id}/participants/page")
    public TrainingService.TrainingParticipantPage participantPage(
            @PathVariable long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = PaginationPolicy.DEFAULT_PAGE_SIZE_TEXT) int pageSize
    ) {
        return trainings.participantPage(id, keyword, page, pageSize);
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
