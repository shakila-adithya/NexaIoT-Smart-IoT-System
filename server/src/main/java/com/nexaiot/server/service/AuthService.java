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
import com.nexaiot.server.exception.InvalidCredentialsException;
import com.nexaiot.server.security.JwtService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public UserResponse register(RegisterRequest request) {

        String email = request.getEmail().trim().toLowerCase();

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

        return new UserResponse(
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }
    
    public AuthResponse login(LoginRequest request) {

        String email = request.getEmail().trim().toLowerCase();

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

        UserResponse userResponse = new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole()
        );

        return new AuthResponse(token, userResponse);
    }
}