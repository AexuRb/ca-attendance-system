package com.ca.attendance.desktop;

import com.ca.attendance.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;

@RestController
@RequestMapping("/api/desktop")
public class DesktopControlController {
    private static final String TOKEN_HEADER = "X-Desktop-Control-Token";
    private final DesktopControlService controls;

    public DesktopControlController(DesktopControlService controls) {
        this.controls = controls;
    }

    @PostMapping("/reset-admin")
    public DesktopControlService.RecoveryResult resetAdministrator(
            @RequestHeader(TOKEN_HEADER) String token,
            @Valid @RequestBody ResetAdministratorRequest request,
            HttpServletRequest servletRequest) {
        requireLoopback(servletRequest);
        return controls.resetAdministrator(token, request.account(), request.newPassword());
    }

    @PostMapping("/shutdown")
    public ResponseEntity<Void> shutdown(@RequestHeader(TOKEN_HEADER) String token,
                                         HttpServletRequest servletRequest) {
        requireLoopback(servletRequest);
        controls.shutdown(token);
        return ResponseEntity.noContent().build();
    }

    private void requireLoopback(HttpServletRequest request) {
        try {
            if (!InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) {
                throw ApiException.forbidden("桌面控制只能在本机使用");
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.forbidden("无法确认本机访问来源");
        }
    }

    public record ResetAdministratorRequest(
            @NotBlank
            @Size(min = 4, max = 32)
            @Pattern(regexp = "[A-Za-z0-9_-]+")
            String account,
            @NotBlank @Size(min = 6, max = 64) String newPassword
    ) {
    }
}
