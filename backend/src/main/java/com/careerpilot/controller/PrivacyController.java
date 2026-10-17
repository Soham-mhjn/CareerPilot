package com.careerpilot.controller;
import com.careerpilot.entity.UserAccount;
import com.careerpilot.repository.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.file.*;
import java.util.Comparator;

@RestController
@RequestMapping("/api/v1/profile")
public class PrivacyController {
    private final UserRepository users;
    private final Path storage;

    public PrivacyController(UserRepository users, @Value("${careerpilot.storage.path:./data/resumes}") String path) {
        this.users = users;
        storage = Path.of(path).toAbsolutePath().normalize();
    }

    public record ProfileInput(@NotBlank @Size(max = 120) String fullName) {
    }

    @PutMapping
    UserView update(Authentication auth, @Valid @RequestBody ProfileInput input) {
        UserAccount u = current(auth);
        u.fullName = input.fullName().trim();
        users.save(u);
        return new UserView(u.id, u.email, u.fullName);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication auth, HttpServletRequest request) {
        UserAccount u = current(auth);
        Path folder = storage.resolve(u.id.toString()).normalize();
        if (folder.startsWith(storage) && Files.exists(folder)) {
            try (var paths = Files.walk(folder)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        }
        users.delete(u);
        if (request.getSession(false) != null) request.getSession(false).invalidate();
    }

    private UserAccount current(Authentication a) {
        return users.findByEmailIgnoreCase(a.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    public record UserView(Long id, String email, String fullName) {
    }
}
