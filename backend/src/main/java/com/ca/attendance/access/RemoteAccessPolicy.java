package com.ca.attendance.access;

import com.ca.attendance.auth.AuthService;
import com.ca.attendance.common.ApiException;
import com.ca.attendance.common.Role;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Arrays;
import java.util.Set;

@Component
public class RemoteAccessPolicy {
    private static final int MAX_HEADER_LENGTH = 255;
    private static final Set<String> PERSONAL_READ = Set.of(
            "/api/auth/me", "/api/attendance/me", "/api/trainings/me", "/api/settings/credits");
    private static final Set<String> MINISTER_READ = Set.of(
            "/api/attendance", "/api/attendance/page", "/api/repairs",
            "/api/stats/summary", "/api/stats/weekly-detail", "/api/stats/member-detail",
            "/api/stats/dashboard", "/api/stats/export", "/api/schedules",
            "/api/public/schedules/today", "/api/public/schedules/week",
            "/api/public/duty-periods", "/api/public/duty-weekdays");
    private final int remotePort;

    public RemoteAccessPolicy(@Value("${app.remote.port:8081}") int remotePort) {
        this.remotePort = remotePort;
    }

    public boolean isRemote(HttpServletRequest request) {
        return remotePort > 0 && request.getLocalPort() == remotePort;
    }

    public boolean roleAllowed(Role role) {
        return RolePermissionPolicy.allows(role, RolePermissionPolicy.Permission.REMOTE_ADMIN);
    }

    /** The request connector determines access, including tokens issued at the local entry. */
    public void requireOperationAllowed(HttpServletRequest request, Role role) {
        if (!isRemote(request) || role == Role.PRESIDENT || role == Role.ADMIN) return;
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method)) {
            if (PERSONAL_READ.contains(path)) return;
            if (role == Role.MINISTER && (MINISTER_READ.contains(path)
                    || path.matches("/api/repairs/[0-9]+/agreement"))) return;
        }
        if (("PUT".equals(method) && path.equals("/api/me/profile"))
                || ("POST".equals(method) && (path.equals("/api/auth/change-password")
                || path.equals("/api/auth/logout")))) return;
        throw ApiException.forbidden(role == Role.MINISTER
                ? "远程部长无权执行此操作" : "当前账号无权访问此功能");
    }

    public AuthService.LoginContext loginContext(HttpServletRequest request) {
        boolean remote = isRemote(request);
        return new AuthService.LoginContext(
                remote,
                remote ? remoteClientAddress(request) : "127.0.0.1",
                limited(request.getHeader("User-Agent"))
        );
    }

    public AccessContext context(HttpServletRequest request) {
        boolean remote = isRemote(request);
        return new AccessContext(
                remote ? "REMOTE_ADMIN" : "LOCAL",
                !remote,
                remote ? Arrays.stream(Role.values()).filter(this::roleAllowed).map(Role::name).toList() : List.of()
        );
    }

    private String remoteClientAddress(HttpServletRequest request) {
        // The tunnel terminates locally, so client-supplied forwarding headers are not trusted.
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
