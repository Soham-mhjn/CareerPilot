package com.careerpilot.controller;
import com.careerpilot.entity.UserAccount;
import com.careerpilot.repository.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;
import com.careerpilot.service.PasswordResetService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SecurityContextRepository contexts;
    private final PasswordResetService passwordResets;

    public AuthController(UserRepository users, PasswordEncoder encoder, SecurityContextRepository contexts, PasswordResetService passwordResets) {
        this.users = users;
        this.encoder = encoder;
        this.contexts = contexts;
        this.passwordResets = passwordResets;
    }

    public record Register(@NotBlank @Size(max = 120) String fullName, @Email @NotBlank String email,
                           @NotBlank @Size(min = 10, max = 72) String password) {
    }

    public record Login(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record PasswordResetRequest(@Email @NotBlank String email) { }

    public record PasswordResetConfirmation(@NotBlank String token,
                                            @NotBlank @Size(min = 10, max = 72) String newPassword) { }

    @PostMapping("/password-reset/request")
    Map<String, String> requestPasswordReset(@Valid @RequestBody PasswordResetRequest body) {
        passwordResets.requestReset(body.email());
        return Map.of("message", "If an account exists for that email, a password reset link has been sent.");
    }

    @PostMapping("/password-reset/confirm")
    Map<String, String> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmation body) {
        passwordResets.resetPassword(body.token(), body.newPassword());
        return Map.of("message", "Your password has been updated. You can sign in now.");
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> register(@Valid @RequestBody Register body, HttpServletRequest request, HttpServletResponse response) {
        String email = body.email().trim().toLowerCase();
        if (users.findByEmailIgnoreCase(email).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        UserAccount user = users.save(new UserAccount(email, body.fullName().trim(), encoder.encode(body.password())));
        authenticate(user, request, response);
        return Map.of("id", user.id, "email", user.email, "fullName", user.fullName);
    }

    @PostMapping("/login")
    Map<String, Object> login(@Valid @RequestBody Login body, HttpServletRequest request, HttpServletResponse response) {
        UserAccount user = users.findByEmailIgnoreCase(body.email().trim()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"));
        if (!encoder.matches(body.password(), user.passwordHash))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
        authenticate(user, request, response);
        return Map.of("id", user.id, "email", user.email, "fullName", user.fullName);
    }

    private void authenticate(UserAccount user, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) request.changeSessionId();
        var auth = new UsernamePasswordAuthenticationToken(user.email, "", java.util.List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
    }

    @GetMapping("/me")
    Map<String, Object> me(java.security.Principal principal) {
        UserAccount user = users.findByEmailIgnoreCase(principal.getName()).orElseThrow();
        return Map.of("id", user.id, "email", user.email, "fullName", user.fullName);
    }
}


