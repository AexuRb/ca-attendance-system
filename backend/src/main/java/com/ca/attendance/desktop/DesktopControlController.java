package com.ca.attendance.desktop;

import com.ca.attendance.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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
}
