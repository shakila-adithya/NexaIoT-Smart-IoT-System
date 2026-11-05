package com.nexaiot.server.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.exception.InvalidCredentialsException;
import com.nexaiot.server.model.User;
import com.nexaiot.server.repository.UserRepository;

class AccountDeletionServiceTest {
    private final UserRepository users = Mockito.mock(UserRepository.class);
    private final PasswordEncoder encoder = Mockito.mock(PasswordEncoder.class);
    private final MongoTemplate mongo = Mockito.mock(MongoTemplate.class);
    private final ObjectProvider<org.springframework.data.mongodb.gridfs.GridFsTemplate> gridFs = mockObjectProvider();
    private final AccountDeletionService service = new AccountDeletionService(users, encoder, mongo, gridFs);

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> mockObjectProvider() {
        return Mockito.mock(ObjectProvider.class);
    }

    @Test
    void selfDeleteRequiresCurrentPasswordAndCascadesOwnedRecords() {
        User user = user("owner@example.com", "USER");
        Device device = new Device();
        device.setId("device-1");
        device.setOwnerEmail(user.getEmail());
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches("correct", user.getPasswordHash())).thenReturn(true);
        when(mongo.find(any(), eq(Device.class))).thenReturn(List.of(device));
        when(mongo.findById("device-1", Device.class)).thenReturn(device);
        when(gridFs.getIfAvailable()).thenReturn(null);

        service.deleteSelf(user.getEmail(), "correct");

        verify(mongo).remove(any(), eq("devices"));
        verify(mongo).remove(any(), eq("sensor_readings"));
        verify(mongo).remove(any(), eq("alerts"));
        verify(mongo).remove(any(), eq("alertRules"));
        verify(mongo).remove(any(), eq("maintenance_tasks"));
        verify(mongo).remove(any(), eq("generated_reports"));
        verify(mongo).remove(any(), eq("provisioning_sessions"));
        verify(mongo).remove(any(), eq("device_enrollment_operations"));
        verify(users).delete(user);
    }

    @Test
    void selfDeleteRejectsWrongPasswordWithoutChangingData() {
        User user = user("owner@example.com", "USER");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", user.getPasswordHash())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> service.deleteSelf(user.getEmail(), "wrong"));

        verify(users, never()).delete(any());
        verify(mongo, never()).remove(any(), any(String.class));
    }

    @Test
    void administratorCannotDeleteTheirOwnAccountThroughAdminPath() {
        User admin = user("admin@example.com", "ADMIN");
        when(users.findById("admin-1")).thenReturn(Optional.of(admin));

        assertThrows(IllegalArgumentException.class, () -> service.deleteUser("admin-1", "ADMIN@example.com"));

        verify(users, never()).delete(any());
    }

    private User user(String email, String role) {
        User user = new User("Test User", email, "hash", role);
        user.setId(email);
        return user;
    }
}
