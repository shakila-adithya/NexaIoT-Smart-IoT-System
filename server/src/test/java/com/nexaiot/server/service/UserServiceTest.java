package com.nexaiot.server.service;

import com.nexaiot.server.dto.ChangePasswordRequest;
import com.nexaiot.server.dto.UpdatePreferencesRequest;
import com.nexaiot.server.dto.UpdateProfileRequest;
import com.nexaiot.server.dto.UserResponse;
import com.nexaiot.server.exception.InvalidCredentialsException;
import com.nexaiot.server.model.User;
import com.nexaiot.server.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void updatesProfileAndPreferencesForAuthenticatedUser() {
        User user = user();
        UserService service = new UserService(repositoryFor(user), passwordEncoder);

        UpdateProfileRequest profile = new UpdateProfileRequest();
        profile.setFirstName("Asha");
        profile.setLastName("Perera");
        profile.setJobTitle("IoT Engineer");
        profile.setTimeZone("Asia/Colombo");

        UserResponse updated = service.updateProfile("owner@example.com", profile);
        assertEquals("Asha Perera", updated.getFullName());
        assertEquals("Asia/Colombo", updated.getTimeZone());

        UpdatePreferencesRequest preferences = new UpdatePreferencesRequest();
        preferences.setLiveChartAnimations(false);
        preferences.setDashboardAutoRefresh(false);

        UserResponse withPreferences = service.updatePreferences("owner@example.com", preferences);
        assertFalse(withPreferences.getLiveChartAnimations());
        assertFalse(withPreferences.getDashboardAutoRefresh());
    }

    @Test
    void changesPasswordOnlyAfterVerifyingCurrentPassword() {
        User user = user();
        UserService service = new UserService(repositoryFor(user), passwordEncoder);

        ChangePasswordRequest wrong = passwordRequest("wrong-password", "new-password", "new-password");
        assertThrows(InvalidCredentialsException.class,
                () -> service.changePassword("owner@example.com", wrong));

        ChangePasswordRequest valid = passwordRequest("old-password", "new-password", "new-password");
        service.changePassword("owner@example.com", valid);

        assertTrue(passwordEncoder.matches("new-password", user.getPasswordHash()));
        assertFalse(passwordEncoder.matches("old-password", user.getPasswordHash()));
    }

    private User user() {
        User user = new User("Existing User", "owner@example.com", passwordEncoder.encode("old-password"), "USER");
        user.setId("user-1");
        return user;
    }

    private UserRepository repositoryFor(User user) {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class[]{UserRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByEmail")) return Optional.of(user);
                    if (method.getName().equals("save")) return args[0];
                    if (method.getName().equals("toString")) return "test-user-repository";
                    if (method.getName().equals("hashCode")) return 1;
                    if (method.getName().equals("equals")) return proxy == args[0];
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private ChangePasswordRequest passwordRequest(String current, String next, String confirmation) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        request.setConfirmPassword(confirmation);
        return request;
    }
}
