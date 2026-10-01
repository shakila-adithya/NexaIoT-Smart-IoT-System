package com.nexaiot.server.controller;

import com.nexaiot.server.dto.AuthResponse;
import com.nexaiot.server.dto.LoginRequest;
import com.nexaiot.server.dto.RegisterRequest;
import com.nexaiot.server.dto.UserResponse;
import com.nexaiot.server.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}