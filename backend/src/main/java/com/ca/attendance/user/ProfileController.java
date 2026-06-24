package com.ca.attendance.user;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
public class ProfileController {
    private final UserService users;

    public ProfileController(UserService users) {
        this.users = users;
    }

    @PutMapping("/profile")
    public void updateProfile(@Valid @RequestBody UserService.ProfileRequest request) {
        users.updateProfile(request);
    }
}
