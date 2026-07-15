package com.ca.attendance.access;

import com.ca.attendance.auth.AuthService;
import com.ca.attendance.common.Role;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RemoteAccessPolicy {
    private static final int MAX_HEADER_LENGTH = 255;
    private final int remotePort;

    public RemoteAccessPolicy(@Value("${app.remote.port:8081}") int remotePort) {
        this.remotePort = remotePort;
    }

    public boolean isRemote(HttpServletRequest request) {
        return remotePort > 0 && request.getLocalPort() == remotePort;
    }

    public boolean roleAllowed(Role role) {
        return role == Role.PRESIDENT || role == Role.ADMIN;
    }

    public AuthService.LoginContext loginContext(HttpServletRequest request) {
        boolean remote = isRemote(request);
        return new AuthService.LoginContext(
                remote,
                remote ? clientAddress(request) : "127.0.0.1",
                limited(request.getHeader("User-Agent"))
        );
    }

    public AccessContext context(HttpServletRequest request) {
        boolean remote = isRemote(request);
        return new AccessContext(
                remote ? "REMOTE_ADMIN" : "LOCAL",
                !remote,
                remote ? List.of(Role.PRESIDENT.name(), Role.ADMIN.name()) : List.of()
        );
    }

    private String clientAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] addresses = forwardedFor.split(",");
            String lastAddress = addresses[addresses.length - 1].trim();
            if (!lastAddress.isBlank()) {
                return limited(lastAddress);
            }
        }
        return limited(request.getRemoteAddr());
    }

    private String limited(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String clean = value.replace("\r", "").replace("\n", "").trim();
        return clean.substring(0, Math.min(clean.length(), MAX_HEADER_LENGTH));
    }

    public record AccessContext(String mode, boolean kioskAvailable, List<String> allowedRemoteRoles) {
    }
}
