package com.nexaiot.server.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nexaiot.server.admin.dto.AdminStatsResponse;
import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.model.User;
import com.nexaiot.server.repository.UserRepository;

class AdminServiceTest {
    @Test
    void filtersInventoryAndComputesAuthoritativeStatistics() {
        User admin = user("admin@example.com", "ADMIN");
        User owner = user("owner@example.com", "USER");
        Device online = device("sensor-1", "online", "owner@example.com", "ONLINE");
        Device offline = device("switch-1", "offline", "owner@example.com", "OFFLINE");
        AdminService service = new AdminService(userRepository(List.of(admin, owner)), deviceRepository(List.of(online, offline)));

        assertEquals(1, service.users("admin").size());
        assertEquals(1, service.devices("switch").size());
        AdminStatsResponse stats = service.stats();
        assertEquals(2, stats.users());
        assertEquals(2, stats.devices());
        assertEquals(1, stats.onlineDevices());
        assertEquals(1, stats.offlineDevices());
    }

    private User user(String email, String role) {
        User user = new User("Test User", email, "hash", role);
        user.setId(email);
        return user;
    }

    private Device device(String key, String name, String owner, String status) {
        Device device = new Device();
        device.setId(key);
        device.setDeviceKey(key);
        device.setName(name);
        device.setOwnerEmail(owner);
        device.setStatus(status);
        return device;
    }

    private UserRepository userRepository(List<User> values) {
        return (UserRepository) Proxy.newProxyInstance(UserRepository.class.getClassLoader(), new Class[]{UserRepository.class}, (proxy, method, args) -> switch (method.getName()) {
            case "findAll" -> values;
            case "count" -> (long) values.size();
            case "findByEmail" -> values.stream().filter(user -> user.getEmail().equals(args[0])).findFirst();
            case "existsByEmail" -> values.stream().anyMatch(user -> user.getEmail().equals(args[0]));
            case "toString" -> "users";
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private DeviceRepository deviceRepository(List<Device> values) {
        return (DeviceRepository) Proxy.newProxyInstance(DeviceRepository.class.getClassLoader(), new Class[]{DeviceRepository.class}, (proxy, method, args) -> switch (method.getName()) {
            case "findAll" -> values;
            case "toString" -> "devices";
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }
}
