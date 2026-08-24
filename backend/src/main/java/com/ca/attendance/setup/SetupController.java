package com.ca.attendance.setup;

import com.ca.attendance.auth.AuthService;
import com.ca.attendance.access.RemoteAccessPolicy;
import com.ca.attendance.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;

@RestController
@RequestMapping("/api/setup")
public class SetupController {
    private final SetupService setupService;
    private final RemoteAccessPolicy remoteAccess;

    public SetupController(SetupService setupService, RemoteAccessPolicy remoteAccess) {
        this.setupService = setupService;
        this.remoteAccess = remoteAccess;
    }

    @GetMapping("/status")
    public SetupService.SetupStatus status(HttpServletRequest request) {
        requireLoopback(request);
        return setupService.status();
    }

    @PostMapping("/initialize")
    public AuthService.LoginResponse initialize(@Valid @RequestBody InitializeRequest request,
                                                HttpServletRequest servletRequest) {
        requireLoopback(servletRequest);
        return setupService.initialize(new SetupService.SetupRequest(
                request.account(),
                request.name(),
                request.password()
        ));
    }

    private void requireLoopback(HttpServletRequest request) {
        if (remoteAccess.isRemote(request)) {
            throw ApiException.forbidden("初始化信息仅限主机本机访问");
        }
        try {
            if (!InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) {
                throw ApiException.forbidden("初始化只能在本机完成");
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.forbidden("无法确认本机访问来源");
        }
    }

    public record InitializeRequest(
            String account,
            String name,
            String password
    ) {
    }
}
