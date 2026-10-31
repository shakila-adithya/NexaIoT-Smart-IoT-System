package com.nexaiot.server.device;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;

import com.nexaiot.server.device.dto.CreateDeviceRequest;
import com.nexaiot.server.device.dto.DeviceResponse;
import com.nexaiot.server.device.dto.UpdateDeviceRequest;
import com.nexaiot.server.device.template.DeviceTemplate;
import com.nexaiot.server.device.template.DeviceTemplateService;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceTemplateService deviceTemplateService;
    private static final long ONLINE_TIMEOUT_SECONDS = 15;

    public DeviceService(
            DeviceRepository deviceRepository,
            DeviceTemplateService deviceTemplateService
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceTemplateService = deviceTemplateService;
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
        device.setConnectivity(normalizeConnectivity(request.getConnectivity()));
        device.setCapabilities(request.getCapabilities());
        DeviceProvisioningInfo provisioningInfo = new DeviceProvisioningInfo();
        provisioningInfo.setStatus(DeviceProvisioningStatus.REGISTERED);
        device.setProvisioningInfo(provisioningInfo);
        boolean hasTemplateId = request.getTemplateId() != null
                && !request.getTemplateId().isBlank();
        boolean hasCustomManifest = request.getCapabilityManifest() != null;

        if (hasTemplateId && hasCustomManifest) {
            throw new IllegalArgumentException(
                    "Provide either templateId or capabilityManifest, not both."
            );
        }

        if (hasTemplateId) {
            DeviceTemplate template = deviceTemplateService.getTemplate(request.getTemplateId().trim());
            device.setCapabilityManifest(
                    CapabilityManifestCopier.copy(template.getCapabilityManifest())
            );
        } else if (hasCustomManifest) {
            CapabilityManifest customManifest = CapabilityManifestCopier.copy(
                    request.getCapabilityManifest()
            );
            customManifest.setSource(CapabilityManifestSource.CUSTOM);
            customManifest.setTemplateId(null);

            if (customManifest.getCapabilities() == null
                    || customManifest.getCapabilities().isEmpty()
                    || !CapabilityManifestValidator.isValid(customManifest)) {
                throw new IllegalArgumentException(
                        "Invalid custom capability manifest."
                );
            }

            device.setCapabilityManifest(customManifest);
        }
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

        if (request.getConnectivity() != null) {
            device.setConnectivity(normalizeConnectivity(request.getConnectivity()));
        }

        if (request.getCapabilities() != null) {
            device.setCapabilities(request.getCapabilities());
        }

        if (request.getCapabilityManifest() != null) {
            if (device.getCapabilityManifest() == null) {
                throw new IllegalArgumentException(
                        "This device does not use a capability manifest."
                );
            }

            CapabilityManifest manifest = CapabilityManifestCopier.copy(
                    request.getCapabilityManifest()
            );
            manifest.setSource(device.getCapabilityManifest().getSource());
            manifest.setTemplateId(device.getCapabilityManifest().getTemplateId());

            if (manifest.getCapabilities() == null
                    || manifest.getCapabilities().isEmpty()
                    || !CapabilityManifestValidator.isValid(manifest)) {
                throw new IllegalArgumentException(
                        "Invalid capability manifest."
                );
            }

            device.setCapabilityManifest(manifest);
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

    public DeviceResponse getControllableDevice(
            String deviceId,
            String ownerEmail
    ) {
        Device device = findOwnedDevice(deviceId, ownerEmail);
        boolean supportsPower = CapabilityManifestAdapter.effectiveManifest(device)
                .getCapabilities()
                .stream()
                .anyMatch(definition ->
                        definition.getCategory() == CapabilityCategory.CONTROL
                                && ("power".equals(definition.getKey())
                                || "digitalOutput".equals(definition.getKey())
                                || "power".equals(definition.getSemanticType())
                                || "digitalOutput".equals(definition.getSemanticType()))
                );

        if (!supportsPower) {
            throw new IllegalArgumentException(
                    "This device does not support power control."
            );
        }

        return toResponse(device);
    }

    public DeviceResponse getControllableDevice(
            String deviceId,
            String ownerEmail,
            String capabilityKey,
            JsonNode value
    ) {
        Device device = findOwnedDevice(deviceId, ownerEmail);
        if (!"ONLINE".equals(getEffectiveStatus(device))) {
            throw new IllegalStateException("Device must be online before a command can be sent.");
        }
        CapabilityDefinition definition = CapabilityManifestAdapter.effectiveManifest(device)
                .getCapabilities().stream()
                .filter(candidate -> capabilityKey.equals(candidate.getKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported device capability."));
        if (definition.getCategory() != CapabilityCategory.CONTROL || definition.isReadOnly()) {
            throw new IllegalArgumentException("Capability is not writable.");
        }
        validateControlValue(definition, value);
        return toResponse(device);
    }

    private void validateControlValue(CapabilityDefinition definition, JsonNode value) {
        if (value == null || value.isNull() || value.isContainerNode()) {
            throw new IllegalArgumentException("Control value is required.");
        }
        switch (definition.getDataType()) {
            case BOOLEAN -> {
                if (!value.isBoolean()) throw new IllegalArgumentException("Control value must be boolean.");
            }
            case NUMBER -> {
                if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
                    throw new IllegalArgumentException("Control value must be numeric.");
                }
                double number = value.asDouble();
                if (definition.getConstraints() != null) {
                    var constraints = definition.getConstraints();
                    if (constraints.getMin() != null && number < constraints.getMin()
                            || constraints.getMax() != null && number > constraints.getMax()) {
                        throw new IllegalArgumentException("Control value is outside its allowed range.");
                    }
                    if (constraints.getStep() != null && constraints.getMin() != null) {
                        double steps = (number - constraints.getMin()) / constraints.getStep();
                        if (Math.abs(steps - Math.rint(steps)) > 1e-9) {
                            throw new IllegalArgumentException("Control value does not match its allowed step.");
                        }
                    }
                }
            }
            case ENUM, STRING -> {
                if (!value.isTextual()) throw new IllegalArgumentException("Control value must be text.");
                if (definition.getDataType() == CapabilityDataType.ENUM
                        && definition.getOptions().stream().noneMatch(option -> option.getValue().equals(value.asText()))) {
                    throw new IllegalArgumentException("Control value is not an allowed option.");
                }
            }
        }
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

        DeviceResponse response = new DeviceResponse(
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
                device.getConnectivity(),
                device.getCapabilities(),
                CapabilityManifestAdapter.effectiveManifest(device)
        );
        response.setReportedStates(device.getReportedStates());
        return response;
    }

    private DeviceConnectivity normalizeConnectivity(DeviceConnectivity requested) {
        if (requested == null) {
            return null;
        }
        if (requested.getType() == null) {
            throw new IllegalArgumentException("Connectivity type is required.");
        }

        DeviceConnectivity connectivity = new DeviceConnectivity();
        connectivity.setType(requested.getType());
        connectivity.setProtocol(ConnectivityProtocol.MQTT);
        return connectivity;
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
