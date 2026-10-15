package com.nexaiot.server.security;

import com.nexaiot.server.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "c2VjdXJlLXRlc3Qta2V5LWZvci1qd3Qtc2lnbmluZy0xMjM0";
    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3600000);
        user = new User("Owner", "owner@example.com", "hash", "USER");
        user.setTokenVersion(3);
    }

    @Test
    void loginTokenContainsCurrentVersionAndMatchingVersionAuthenticates() {
        String token = jwtService.generateToken(user);

        assertTrue(jwtService.isTokenValid(token, user.getEmail(), 3));
        assertFalse(jwtService.isTokenValid(token, user.getEmail(), 4));
    }

    @Test
    void passwordChangeVersionInvalidatesOldTokenButNewLoginWorks() {
        String oldToken = jwtService.generateToken(user);
        user.setTokenVersion(4);
        String newToken = jwtService.generateToken(user);

        assertFalse(jwtService.isTokenValid(oldToken, user.getEmail(), 4));
        assertTrue(jwtService.isTokenValid(newToken, user.getEmail(), 4));
    }

    @Test
    void existingUserWithoutVersionAcceptsLegacyTokenAsVersionZero() {
        user.setTokenVersion(null);
        String legacyToken = legacyToken(user.getEmail(), 3600000);

        assertTrue(jwtService.isTokenValid(legacyToken, user.getEmail(), 0));
        assertTrue(jwtService.isTokenValid(jwtService.generateToken(user), user.getEmail(), 0));
    }

    @Test
    void expiredTokenIsRejected() {
        String token = legacyToken(user.getEmail(), -1000);

        assertThrows(RuntimeException.class, () -> jwtService.isTokenValid(token, user.getEmail(), 0));
    }

    @Test
    void malformedTokenIsRejectedByParsingBoundary() {
        assertThrows(RuntimeException.class, () -> jwtService.isTokenValid("not-a-jwt", user.getEmail(), 3));
    }

    @Test
    void nonNumericVersionClaimIsRejected() {
        Date now = new Date();
        String token = Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole())
                .claim("tokenVersion", "three")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600000))
                .signWith(key())
                .compact();

        assertFalse(jwtService.isTokenValid(token, user.getEmail(), 0));
    }

    private String legacyToken(String email, long lifetimeMillis) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("role", "USER")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + lifetimeMillis))
                .signWith(key())
                .compact();
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(SECRET));
    }
}
