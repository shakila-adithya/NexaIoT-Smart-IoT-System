package com.nexaiot.server.service;

import com.nexaiot.server.dto.ChangePasswordRequest;
import com.nexaiot.server.dto.UpdatePreferencesRequest;
import com.nexaiot.server.dto.UpdateProfileRequest;
import com.nexaiot.server.dto.UserResponse;
import com.nexaiot.server.exception.InvalidCredentialsException;
import com.nexaiot.server.exception.UserNotFoundException;
import com.nexaiot.server.model.User;
import com.nexaiot.server.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

@Service
public class UserService {

    private static final String CELSIUS = "CELSIUS";
    private static final String FAHRENHEIT = "FAHRENHEIT";
    private static final String DEFAULT_DATE_FORMAT = "DD/MM/YYYY";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse getCurrentUser(String email) {
        return UserResponse.from(findUser(email));
    }

    public UserResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = findUser(email);

        if (request.getFirstName() != null) user.setFirstName(clean(request.getFirstName()));
        if (request.getLastName() != null) user.setLastName(clean(request.getLastName()));
        if (request.getPhoneNumber() != null) user.setPhoneNumber(clean(request.getPhoneNumber()));
        if (request.getJobTitle() != null) user.setJobTitle(clean(request.getJobTitle()));
        if (request.getDepartment() != null) user.setDepartment(clean(request.getDepartment()));
        if (request.getBio() != null) user.setBio(clean(request.getBio()));

        if (request.getTimeZone() != null && !request.getTimeZone().isBlank()) {
            String timeZone = request.getTimeZone().trim();
            try {
                ZoneId.of(timeZone);
            } catch (Exception exception) {
                throw new IllegalArgumentException("Please select a valid time zone");
            }
            user.setTimeZone(timeZone);
        } else if (request.getTimeZone() != null) {
            user.setTimeZone(null);
        }

        String fullName = String.join(" ",
                user.getFirstName() == null ? "" : user.getFirstName(),
                user.getLastName() == null ? "" : user.getLastName()
        ).trim().replaceAll("\\s+", " ");
        if (!fullName.isBlank()) {
            user.setFullName(fullName);
        }

        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse updatePreferences(String email, UpdatePreferencesRequest request) {
        User user = findUser(email);

        if (request.getTemperatureUnit() != null) {
            String value = request.getTemperatureUnit().trim().toUpperCase();
            if (!CELSIUS.equals(value) && !FAHRENHEIT.equals(value)) {
                throw new IllegalArgumentException("Unsupported temperature unit");
            }
            user.setTemperatureUnit(value);
        }

        if (request.getDateFormat() != null) {
            String value = request.getDateFormat().trim();
            if (!DEFAULT_DATE_FORMAT.equals(value) && !"MM/DD/YYYY".equals(value)
                    && !"YYYY-MM-DD".equals(value)) {
                throw new IllegalArgumentException("Unsupported date format");
            }
            user.setDateFormat(value);
        }

        if (request.getCompactMode() != null) {
            user.setCompactMode(request.getCompactMode());
        }
        if (request.getLiveChartAnimations() != null) {
            user.setLiveChartAnimations(request.getLiveChartAnimations());
        }
        if (request.getDashboardAutoRefresh() != null) {
            user.setDashboardAutoRefresh(request.getDashboardAutoRefresh());
        }

        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse changePassword(String email, ChangePasswordRequest request) {
        User user = findUser(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New passwords do not match");
        }
        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        return UserResponse.from(userRepository.save(user));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }
}
