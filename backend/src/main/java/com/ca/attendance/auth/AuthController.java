package com.ca.attendance.auth;

import com.ca.attendance.access.RemoteAccessPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final RemoteAccessPolicy remoteAccess;

    public AuthController(AuthService authService, RemoteAccessPolicy remoteAccess) {
        this.authService = authService;
        this.remoteAccess = remoteAccess;
    }

    @PostMapping("/login")
    public AuthService.LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return authService.login(request.studentNo(), request.password(), remoteAccess.loginContext(servletRequest));
    }

    @GetMapping("/me")
    public Object me() {
        return authService.me();
    }

    @PostMapping("/change-password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request.oldPassword(), request.newPassword());
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @RequestHeader(value = "X-Auth-Token", required = false) String token) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authService.logout(authorization.substring(7).trim());
        } else {
            authService.logout(token);
        }
    }

    public record LoginRequest(@NotBlank @Size(max = 64) String studentNo,
                               @NotBlank @Size(max = 128) String password) {
    }

    public record ChangePasswordRequest(@NotBlank String oldPassword, String newPassword) {
    }
}
