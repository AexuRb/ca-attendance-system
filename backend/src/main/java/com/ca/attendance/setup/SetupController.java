package com.ca.attendance.setup;

import com.ca.attendance.auth.AuthService;
import com.ca.attendance.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
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
            @NotBlank
            @Size(min = 4, max = 32)
            @Pattern(regexp = "[A-Za-z0-9_-]+", message = "管理员账号只能包含字母、数字、下划线和短横线")
            String account,
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(min = 6, max = 64) String password
    ) {
    }
}
