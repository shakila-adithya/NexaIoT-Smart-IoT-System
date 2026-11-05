package com.nexaiot.server.provisioning;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Service;

@Service
public class PairingTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateToken() {
        byte[] token = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    public String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Pairing token is required.");
        }

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public boolean matches(String token, String expectedHash) {
        if (token == null || expectedHash == null) {
            return false;
        }

        return constantTimeEquals(hashToken(token), expectedHash);
    }

    public boolean matchesExpectedIdentity(String candidateIdentityHash, String expectedIdentityHash) {
        if (candidateIdentityHash == null || expectedIdentityHash == null
                || candidateIdentityHash.isBlank() || expectedIdentityHash.isBlank()) {
            return false;
        }
        return constantTimeEquals(candidateIdentityHash, expectedIdentityHash);
    }

    private boolean constantTimeEquals(String actualValue, String expectedValue) {
        byte[] actual = actualValue.getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedValue.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }
}
