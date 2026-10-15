package com.nexaiot.server.controller;

import com.nexaiot.server.dto.AuthResponse;
import com.nexaiot.server.dto.LoginRequest;
import com.nexaiot.server.dto.RegisterRequest;
import com.nexaiot.server.dto.UserResponse;
import com.nexaiot.server.dto.ForgotPasswordRequest;
import com.nexaiot.server.dto.ResetPasswordRequest;
import com.nexaiot.server.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import com.nexaiot.server.security.RateLimitProperties;
import com.nexaiot.server.security.RateLimitService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;

    public AuthController(
            AuthService authService,
            RateLimitService rateLimitService,
            RateLimitProperties rateLimitProperties
    ) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        rateLimitService.requireAllowed(
                "register-ip", httpRequest.getRemoteAddr(),
                rateLimitProperties.getRegisterMax(), rateLimitProperties.getRegisterWindowSeconds());
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        enforceLoginLimits(request, httpRequest);
        return authService.login(request);
    }

    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        enforcePasswordResetLimits(request.getEmail(), httpRequest, "forgot");
        return Map.of("message", authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        enforcePasswordResetLimits(request.getToken(), httpRequest, "reset");
        return Map.of("message", authService.resetPassword(request));
    }

    private void enforceLoginLimits(LoginRequest request, HttpServletRequest httpRequest) {
        rateLimitService.requireAllowed(
                "login-ip", httpRequest.getRemoteAddr(),
                rateLimitProperties.getLoginMax(), rateLimitProperties.getLoginWindowSeconds());
        rateLimitService.requireAllowed(
                "login-email", normalize(request.getEmail()),
                rateLimitProperties.getLoginMax(), rateLimitProperties.getLoginWindowSeconds());
    }

    private void enforcePasswordResetLimits(
            String identifier,
            HttpServletRequest httpRequest,
            String operation
    ) {
        rateLimitService.requireAllowed(
                "password-reset-ip-" + operation, httpRequest.getRemoteAddr(),
                rateLimitProperties.getPasswordResetMax(), rateLimitProperties.getPasswordResetWindowSeconds());
        rateLimitService.requireAllowed(
                "password-reset-identifier-" + operation, normalize(identifier),
                rateLimitProperties.getPasswordResetMax(), rateLimitProperties.getPasswordResetWindowSeconds());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
