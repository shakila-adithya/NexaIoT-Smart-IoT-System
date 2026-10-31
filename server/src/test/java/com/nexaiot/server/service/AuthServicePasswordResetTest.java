package com.nexaiot.server.service;

import com.nexaiot.server.dto.ForgotPasswordRequest;
import com.nexaiot.server.dto.ResetPasswordRequest;
import com.nexaiot.server.model.PasswordResetToken;
import com.nexaiot.server.model.User;
import com.nexaiot.server.repository.PasswordResetTokenRepository;
import com.nexaiot.server.repository.UserRepository;
import com.nexaiot.server.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthServicePasswordResetTest {

    private static final String TEST_JWT_SECRET = "c2VjdXJlLXRlc3Qta2V5LWZvci1qd3Qtc2lnbmluZy0xMjM0";

    @Test
    void resetUsesSingleUseHashedTokenAndUpdatesPassword() throws Exception {
        User user = user();
        List<PasswordResetToken> tokens = new ArrayList<>();
        String rawToken = "secure-reset-token";
        PasswordResetToken token = token(user, rawToken, Instant.now().plusSeconds(600));
        tokens.add(token);

        AuthService service = service(user, tokens, null);
        JwtService jwtService = new JwtService(TEST_JWT_SECRET, 3600000);
        String oldToken = jwtService.generateToken(user);
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(rawToken);
        request.setNewPassword("new-password");

        assertEquals("Password reset successfully.", service.resetPassword(request));
        assertTrue(new BCryptPasswordEncoder().matches("new-password", user.getPasswordHash()));
        assertEquals(1, user.getTokenVersion());
        assertFalse(jwtService.isTokenValid(oldToken, user.getEmail(), user.getTokenVersion()));
        assertNotNull(token.getUsedAt());
        assertNull(token.getRevokedAt());
        assertEquals(sha256(rawToken), token.getTokenHash());

        assertThrows(RuntimeException.class, () -> service.resetPassword(request));
    }

    @Test
    void forgotPasswordRevokesPreviousTokenAndReturnsGenericResponse() {
        User user = user();
        List<PasswordResetToken> tokens = new ArrayList<>();
        PasswordResetToken previous = token(user, "previous", Instant.now().plusSeconds(600));
        tokens.add(previous);
        AuthService service = service(user, tokens, null);

        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(" OWNER@example.com ");

        assertEquals(AuthService.GENERIC_RESET_RESPONSE, service.forgotPassword(request));
        assertNotNull(previous.getRevokedAt());
        assertEquals(2, tokens.size());
        assertNotEquals("previous", tokens.get(1).getTokenHash());
    }

    private AuthService service(User user, List<PasswordResetToken> tokens, JavaMailSender sender) {
        JavaMailSender mailSender = sender == null ? (JavaMailSender) Proxy.newProxyInstance(
                JavaMailSender.class.getClassLoader(),
                new Class[]{JavaMailSender.class},
                (proxy, method, args) -> null
        ) : sender;
        PasswordResetEmailService emailService = new PasswordResetEmailService(
                mailSender, "noreply@nexaiot.local", "http://localhost:5173", 30);
        return new AuthService(
                userRepository(user), new BCryptPasswordEncoder(),
                new JwtService(TEST_JWT_SECRET, 3600000),
                tokenRepository(tokens), emailService, 30);
    }

    private User user() {
        User user = new User("Owner", "owner@example.com", new BCryptPasswordEncoder().encode("old-password"), "USER");
        user.setId("user-1");
        return user;
    }

    private PasswordResetToken token(User user, String rawToken, Instant expiresAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setUserEmail(user.getEmail());
        token.setTokenHash(sha256(rawToken));
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(expiresAt);
        return token;
    }

    private UserRepository userRepository(User user) {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(), new Class[]{UserRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByEmail", "findById" -> Optional.of(user);
                    case "save" -> args[0];
                    case "toString" -> "test-user-repository";
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private PasswordResetTokenRepository tokenRepository(List<PasswordResetToken> tokens) {
        return (PasswordResetTokenRepository) Proxy.newProxyInstance(
                PasswordResetTokenRepository.class.getClassLoader(), new Class[]{PasswordResetTokenRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByTokenHash" -> tokens.stream()
                            .filter(token -> token.getTokenHash().equals(args[0]))
                            .findFirst();
                    case "findByUserIdAndUsedAtIsNullAndRevokedAtIsNull" -> tokens.stream()
                            .filter(token -> token.getUserId().equals(args[0])
                                    && token.getUsedAt() == null && token.getRevokedAt() == null)
                            .toList();
                    case "save" -> {
                        if (!tokens.contains(args[0])) tokens.add((PasswordResetToken) args[0]);
                        yield args[0];
                    }
                    case "saveAll" -> {
                        yield args[0];
                    }
                    case "toString" -> "test-token-repository";
                    case "hashCode" -> 2;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
