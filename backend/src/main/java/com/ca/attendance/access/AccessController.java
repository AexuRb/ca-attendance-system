package com.ca.attendance.access;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/access")
public class AccessController {
    private final RemoteAccessPolicy policy;

    public AccessController(RemoteAccessPolicy policy) {
        this.policy = policy;
    }

    @GetMapping("/context")
    public RemoteAccessPolicy.AccessContext context(HttpServletRequest request) {
        return policy.context(request);
    }
}
