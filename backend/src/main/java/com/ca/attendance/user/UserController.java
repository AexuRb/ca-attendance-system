package com.ca.attendance.user;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public List<UserSummary> search(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String role,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String grade) {
        return users.search(keyword, role, status, grade);
    }

    @GetMapping("/page")
    public UserRepository.UserPage searchPage(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String role,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String grade,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int pageSize) {
        return users.searchPage(keyword, role, status, grade, page, pageSize);
    }

    @GetMapping("/grades")
    public List<String> grades() {
        return users.grades();
    }

    @PostMapping
    public UserSummary create(@Valid @RequestBody UserService.CreateUserRequest request) {
        return users.create(request);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserService.ImportResult importMembers(@RequestParam("file") MultipartFile file) {
        return users.importMembers(file);
    }

    @PutMapping("/bulk-status")
    public UserService.BulkStatusResult bulkStatus(@Valid @RequestBody UserService.BulkStatusRequest request) {
        return users.bulkStatus(request);
    }

    @PutMapping("/{id}")
    public UserSummary update(@PathVariable long id, @Valid @RequestBody UserService.UpdateUserRequest request) {
        return users.update(id, request);
    }

    @PostMapping("/{id}/reset-password")
    public void resetPassword(@PathVariable long id, @Valid @RequestBody UserService.ResetPasswordRequest request) {
        users.resetPassword(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        users.delete(id);
    }
}
