package com.ca.attendance.log;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/logs")
public class OperationLogController {
    private final OperationLogQueryService logs;

    public OperationLogController(OperationLogQueryService logs) {
        this.logs = logs;
    }

    @GetMapping
    public OperationLogQueryService.LogPage search(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String actionType,
                                                   @RequestParam(required = false) String from,
                                                   @RequestParam(required = false) String to,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int pageSize) {
        return logs.search(keyword, actionType, from, to, page, pageSize);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String actionType,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        String filename = "操作日志.xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(logs.export(keyword, actionType, from, to));
    }

    @DeleteMapping
    public OperationLogQueryService.ClearResult clear() {
        return logs.clear();
    }
}
