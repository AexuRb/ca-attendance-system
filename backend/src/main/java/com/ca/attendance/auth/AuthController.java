package com.ca.attendance.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthService.LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.studentNo(), request.password());
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

    public record LoginRequest(@NotBlank String studentNo, @NotBlank String password) {
    }

    public record ChangePasswordRequest(@NotBlank String oldPassword, @NotBlank @Size(min = 6, max = 64) String newPassword) {
    }
}
