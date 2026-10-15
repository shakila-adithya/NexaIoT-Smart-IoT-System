package com.nexaiot.server.security;

import com.nexaiot.server.model.User;
import com.nexaiot.server.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "c2VjdXJlLXRlc3Qta2V5LWZvci1qd3Qtc2lnbmluZy0xMjM0";
    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3600000);
        user = new User("Owner", "owner@example.com", "hash", "USER");
        user.setTokenVersion(2);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void matchingTokenVersionAuthenticatesRequest() throws Exception {
        runFilter(jwtService.generateToken(user));

        assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
        assertEquals(user.getEmail(), SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void mismatchedTokenVersionLeavesRequestUnauthenticated() throws Exception {
        String token = jwtService.generateToken(user);
        user.setTokenVersion(3);

        runFilter(token);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void legacyTokenWithoutVersionUsesMissingUserVersionAsZero() throws Exception {
        user.setTokenVersion(null);
        User legacyUser = user;
        JwtService legacyTokenService = new JwtService(SECRET, 3600000);
        String token = legacyTokenService.generateToken(legacyUser);

        runFilter(token);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void runFilter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        new JwtAuthenticationFilter(jwtService, repository()).doFilter(request, response, chain);
    }

    private UserRepository repository() {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class[]{UserRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByEmail" -> Optional.of(user);
                    case "toString" -> "jwt-test-user-repository";
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
