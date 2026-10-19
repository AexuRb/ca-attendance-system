package com.ca.attendance.user;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
public class MemberImportTemplateController {
    private final MemberImportTemplateService templates;

    public MemberImportTemplateController(MemberImportTemplateService templates) {
        this.templates = templates;
    }

    @GetMapping("/api/users/import-template")
    public ResponseEntity<byte[]> download() {
        var file = templates.generate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.bytes());
    }
}
