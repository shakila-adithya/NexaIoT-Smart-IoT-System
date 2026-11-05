package com.nexaiot.server.admin;

import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.nexaiot.server.admin.dto.AdminDeviceResponse;
import com.nexaiot.server.admin.dto.AdminStatsResponse;
import com.nexaiot.server.admin.dto.AdminUserResponse;
import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.model.User;
import com.nexaiot.server.repository.UserRepository;

@Service
public class AdminService {
    private static final Comparator<Instant> NEWEST_FIRST = Comparator.nullsLast(Comparator.reverseOrder());
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;

    public AdminService(UserRepository userRepository, DeviceRepository deviceRepository) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
    }

    public List<AdminUserResponse> users(String search) {
        String query = normalizeSearch(search);
        return userRepository.findAll().stream()
                .filter(user -> query.isBlank() || contains(user.getEmail(), query) || contains(user.getFullName(), query) || contains(user.getRole(), query))
                .sorted(Comparator.comparing(User::getCreatedAt, NEWEST_FIRST))
                .map(this::userResponse)
                .toList();
    }

    public List<AdminDeviceResponse> devices(String search) {
        String query = normalizeSearch(search);
        return deviceRepository.findAll().stream()
                .filter(device -> query.isBlank() || contains(device.getName(), query) || contains(device.getDeviceKey(), query) || contains(device.getType(), query) || contains(device.getStatus(), query) || contains(device.getOwnerEmail(), query))
                .sorted(Comparator.comparing(Device::getCreatedAt, NEWEST_FIRST))
                .map(this::deviceResponse)
                .toList();
    }

    public AdminStatsResponse stats() {
        List<Device> devices = deviceRepository.findAll();
        long online = devices.stream().filter(device -> "ONLINE".equals(device.getStatus())).count();
        long offline = devices.stream().filter(device -> "OFFLINE".equals(device.getStatus())).count();
        long maintenance = devices.stream().filter(device -> "MAINTENANCE".equals(device.getStatus())).count();
        return new AdminStatsResponse(userRepository.count(), devices.size(), online, offline, maintenance, devices.size() - online - offline - maintenance);
    }

    private AdminUserResponse userResponse(User user) {
        return new AdminUserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }

    private AdminDeviceResponse deviceResponse(Device device) {
        return new AdminDeviceResponse(device.getId(), device.getDeviceKey(), device.getName(), device.getType(), device.getStatus(), device.getOwnerEmail(), device.getCreatedAt(), device.getLastSeenAt());
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
