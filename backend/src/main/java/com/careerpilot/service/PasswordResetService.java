package com.careerpilot.service;

import com.careerpilot.entity.PasswordResetToken;
import com.careerpilot.entity.UserAccount;
import com.careerpilot.repository.PasswordResetTokenRepository;
import com.careerpilot.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class PasswordResetService {
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final JavaMailSender mailSender;
    private final SecureRandom random = new SecureRandom();
    private final boolean mailEnabled;
    private final String from;
    private final String frontendUrl;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
                                PasswordEncoder encoder, JavaMailSender mailSender,
                                @Value("${careerpilot.mail.enabled:false}") boolean mailEnabled,
                                @Value("${careerpilot.mail.from:}") String from,
                                @Value("${careerpilot.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.from = from;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void requestReset(String email) {
        if (!mailEnabled || from.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Password reset email is not configured. Add SMTP settings to the project-root .env file.");

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        UserAccount user = users.findByEmailIgnoreCase(normalized).orElse(null);
        if (user == null) return;

        tokens.deleteAllByExpiresAtBefore(Instant.now());
        tokens.deleteAllByUserId(user.id);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String resetUrl = frontendUrl + "/?resetToken=" + rawToken;
        tokens.save(new PasswordResetToken(user, hash(rawToken), Instant.now().plus(TOKEN_LIFETIME)));

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(user.email);
            message.setSubject("Reset your CareerPilot password");
            message.setText("A password reset was requested for your CareerPilot account.\n\n"
                    + "Use this link within 30 minutes to choose a new password:\n" + resetUrl
                    + "\n\nIf you did not request this, you can ignore this email.");
            mailSender.send(message);
        } catch (RuntimeException exception) {
            tokens.deleteAllByUserId(user.id);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The password reset email could not be sent. Check the SMTP settings and try again.");
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = tokens.findByTokenHash(hash(rawToken))
                .filter(t -> t.usedAt == null && t.expiresAt.isAfter(Instant.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This password reset link is invalid or has expired. Request a new one."));
        Long userId = token.user.getId();
        if (userId == null || users.updatePasswordHash(userId, encoder.encode(newPassword)) != 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This password reset link is invalid or has expired. Request a new one.");
        token.usedAt = Instant.now();
        tokens.saveAndFlush(token);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
