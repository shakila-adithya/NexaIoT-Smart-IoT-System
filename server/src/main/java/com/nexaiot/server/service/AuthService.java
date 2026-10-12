package com.nexaiot.server.service;

import com.nexaiot.server.dto.RegisterRequest;
import com.nexaiot.server.dto.UserResponse;
import com.nexaiot.server.model.User;
import com.nexaiot.server.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.nexaiot.server.exception.EmailAlreadyExistsException;
import com.nexaiot.server.dto.AuthResponse;
import com.nexaiot.server.dto.LoginRequest;
import com.nexaiot.server.dto.ForgotPasswordRequest;
import com.nexaiot.server.dto.ResetPasswordRequest;
import com.nexaiot.server.exception.InvalidCredentialsException;
import com.nexaiot.server.exception.InvalidPasswordResetTokenException;
import com.nexaiot.server.model.PasswordResetToken;
import com.nexaiot.server.repository.PasswordResetTokenRepository;
import com.nexaiot.server.security.JwtService;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetEmailService passwordResetEmailService;
    private final long passwordResetExpiryMinutes;
    private final SecureRandom secureRandom = new SecureRandom();

    public static final String GENERIC_RESET_RESPONSE =
            "If an account exists for this email, a password reset link has been sent.";

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordResetEmailService passwordResetEmailService,
            @Value("${app.password-reset-expiry-minutes:30}") long passwordResetExpiryMinutes
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordResetEmailService = passwordResetEmailService;
        this.passwordResetExpiryMinutes = passwordResetExpiryMinutes;
    }

    public UserResponse register(RegisterRequest request) {

        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("Email is already registered");
        }

        User user = new User(
                request.getFullName().trim(),
                email,
                passwordEncoder.encode(request.getPassword()),
                "USER"
        );

        User savedUser = userRepository.save(user);

        return UserResponse.from(savedUser);
    }
    
    public AuthResponse login(LoginRequest request) {

        String email = normalizeEmail(request.getEmail());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new InvalidCredentialsException("Invalid email or password")
                );

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash()
        )) {
            throw new InvalidCredentialsException(
                    "Invalid email or password"
            );
        }

        String token = jwtService.generateToken(user);

        UserResponse userResponse = UserResponse.from(user);

        return new AuthResponse(token, userResponse);
    }

    public String forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.getEmail());
        userRepository.findByEmail(email).ifPresent(user -> {
            Instant now = Instant.now();
            List<PasswordResetToken> previousTokens = passwordResetTokenRepository
                    .findByUserIdAndUsedAtIsNullAndRevokedAtIsNull(user.getId());
            previousTokens.forEach(token -> token.setRevokedAt(now));
            passwordResetTokenRepository.saveAll(previousTokens);

            String rawToken = generateRawToken();
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUserId(user.getId());
            resetToken.setUserEmail(user.getEmail());
            resetToken.setTokenHash(hashToken(rawToken));
            resetToken.setCreatedAt(now);
            resetToken.setExpiresAt(now.plus(Duration.ofMinutes(passwordResetExpiryMinutes)));
            PasswordResetToken savedToken = passwordResetTokenRepository.save(resetToken);

            try {
                passwordResetEmailService.send(user.getEmail(), rawToken);
            } catch (RuntimeException ex) {
                savedToken.setRevokedAt(Instant.now());
                passwordResetTokenRepository.save(savedToken);
            }
        });

        return GENERIC_RESET_RESPONSE;
    }

    public String resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHash(hashToken(request.getToken()))
                .orElseThrow(this::invalidResetToken);

        Instant now = Instant.now();
        if (resetToken.getUsedAt() != null
                || resetToken.getRevokedAt() != null
                || resetToken.getExpiresAt() == null
                || !resetToken.getExpiresAt().isAfter(now)) {
            throw invalidResetToken();
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(this::invalidResetToken);
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsedAt(now);
        passwordResetTokenRepository.save(resetToken);

        List<PasswordResetToken> remainingTokens = passwordResetTokenRepository
                .findByUserIdAndUsedAtIsNullAndRevokedAtIsNull(user.getId());
        remainingTokens.forEach(token -> token.setRevokedAt(now));
        passwordResetTokenRepository.saveAll(remainingTokens);

        return "Password reset successfully.";
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to create password reset token.", ex);
        }
    }

    private InvalidPasswordResetTokenException invalidResetToken() {
        return new InvalidPasswordResetTokenException("This password reset link is invalid or has expired.");
    }
}
