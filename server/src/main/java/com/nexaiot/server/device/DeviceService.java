package com.nexaiot.server.device;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.nexaiot.server.device.dto.CreateDeviceRequest;
import com.nexaiot.server.device.dto.DeviceResponse;
import com.nexaiot.server.device.dto.UpdateDeviceRequest;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private static final long ONLINE_TIMEOUT_SECONDS = 5;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public DeviceResponse createDevice(
            CreateDeviceRequest request,
            String ownerEmail
    ) {
        Instant now = Instant.now();

        Device device = new Device();
        device.setDeviceKey(generateDeviceKey());
        device.setOwnerEmail(normalizeEmail(ownerEmail));
        device.setName(request.getName().trim());
        device.setType(request.getType().trim());
        device.setLocation(request.getLocation().trim());
        device.setCapabilities(request.getCapabilities());
        device.setStatus("OFFLINE");
        device.setPowerOn(false);
        device.setCreatedAt(now);
        device.setUpdatedAt(now);

        Device savedDevice = deviceRepository.save(device);

        return toResponse(savedDevice);
    }

    public List<DeviceResponse> getDevices(String ownerEmail) {
        return deviceRepository
                .findByOwnerEmail(normalizeEmail(ownerEmail))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public DeviceResponse getDevice(
            String deviceId,
            String ownerEmail
    ) {
        Device device = findOwnedDevice(deviceId, ownerEmail);

        return toResponse(device);
    }

    public DeviceResponse updateDevice(
            String deviceId,
            UpdateDeviceRequest request,
            String ownerEmail
    ) {
        Device device = findOwnedDevice(deviceId, ownerEmail);

        if (request.getName() != null && !request.getName().isBlank()) {
            device.setName(request.getName().trim());
        }

        if (request.getType() != null && !request.getType().isBlank()) {
            device.setType(request.getType().trim());
        }

        if (request.getLocation() != null && !request.getLocation().isBlank()) {
            device.setLocation(request.getLocation().trim());
        }

        if (request.getCapabilities() != null) {
            device.setCapabilities(request.getCapabilities());
        }

        device.setUpdatedAt(Instant.now());

        Device updatedDevice = deviceRepository.save(device);

        return toResponse(updatedDevice);
    }

    public void deleteDevice(
            String deviceId,
            String ownerEmail
    ) {
        Device device = findOwnedDevice(deviceId, ownerEmail);

        deviceRepository.delete(device);
    }

    private Device findOwnedDevice(
            String deviceId,
            String ownerEmail
    ) {
        return deviceRepository
                .findByIdAndOwnerEmail(
                        deviceId,
                        normalizeEmail(ownerEmail)
                )
                .orElseThrow(
                        () -> new DeviceNotFoundException(
                                "Device not found"
                        )
                );
    }

    private String generateDeviceKey() {
        String deviceKey;

        do {
            deviceKey = "NEXA-" +
                    UUID.randomUUID()
                            .toString()
                            .replace("-", "")
                            .substring(0, 12)
                            .toUpperCase();
        } while (deviceRepository.existsByDeviceKey(deviceKey));

        return deviceKey;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private DeviceResponse toResponse(Device device) {
        String effectiveStatus = getEffectiveStatus(device);

        return new DeviceResponse(
                device.getId(),
                device.getDeviceKey(),
                device.getName(),
                device.getType(),
                device.getLocation(),
                effectiveStatus,
                device.isPowerOn(),
                device.getCreatedAt(),
                device.getUpdatedAt(),
                device.getLastSeenAt(),
                device.getCapabilities()
        );
    }

    private String getEffectiveStatus(Device device) {
        if ("MAINTENANCE".equalsIgnoreCase(device.getStatus())) {
            return "MAINTENANCE";
        }

        Instant lastSeenAt = device.getLastSeenAt();

        if (lastSeenAt == null) {
            return "OFFLINE";
        }

        Instant onlineThreshold =
                Instant.now().minusSeconds(ONLINE_TIMEOUT_SECONDS);

        return lastSeenAt.isAfter(onlineThreshold)
                ? "ONLINE"
                : "OFFLINE";
    }
}
