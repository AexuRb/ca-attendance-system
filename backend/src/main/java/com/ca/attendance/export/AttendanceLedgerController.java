package com.ca.attendance.export;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/exports/attendance/templates")
public class AttendanceLedgerController {
    private final AttendanceLedgerService service;
    public AttendanceLedgerController(AttendanceLedgerService service){this.service=service;}
    @PostMapping("/preview") public AttendanceLedgerService.Preview preview(@RequestBody AttendanceLedgerService.Request request){return service.preview(request);}
    @PostMapping("/excel") public ResponseEntity<byte[]> export(@RequestBody AttendanceLedgerService.Request request){
        var file=service.export(request);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.filename(),StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(file.bytes());
    }
}
