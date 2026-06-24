package com.ca.attendance.log;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
