package com.ca.attendance.export;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/exports")
public class CustomExportController {
    private final CustomExportService exports;

    public CustomExportController(CustomExportService exports) {
        this.exports = exports;
    }

    @GetMapping("/options")
    public CustomExportService.ExportOptions options() {
        return exports.options();
    }

    @PostMapping("/preview")
    public CustomExportService.ExportPreview preview(@RequestBody CustomExportService.ExportRequest request) {
        return exports.preview(request);
    }

    @PostMapping("/excel")
    public ResponseEntity<byte[]> export(@RequestBody CustomExportService.ExportRequest request) {
        CustomExportService.ExportFile file = exports.export(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .header("X-Export-Row-Count", String.valueOf(file.rowCount()))
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.bytes());
    }
}
