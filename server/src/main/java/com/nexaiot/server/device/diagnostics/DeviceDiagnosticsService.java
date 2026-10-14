package com.nexaiot.server.device.diagnostics;

import com.nexaiot.server.alert.Alert;
import com.nexaiot.server.alert.AlertRepository;
import com.nexaiot.server.alert.AlertSeverity;
import com.nexaiot.server.alert.AlertStatus;
import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.device.CapabilityManifestAdapter;
import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceNotFoundException;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.sensor.SensorReading;
import com.nexaiot.server.sensor.SensorReadingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DeviceDiagnosticsService {

    private static final long ONLINE_TIMEOUT_SECONDS = 5;
    private static final int ACTIVITY_LIMIT = 20;

    @Value("${alerts.device-offline-timeout-seconds:15}")
    private long telemetryFreshnessThresholdSeconds = 15;

    private final DeviceRepository deviceRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final AlertRepository alertRepository;

    public DeviceDiagnosticsService(
            DeviceRepository deviceRepository,
            SensorReadingRepository sensorReadingRepository,
            AlertRepository alertRepository
    ) {
        this.deviceRepository = deviceRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.alertRepository = alertRepository;
    }

    public DeviceDiagnosticsResponse diagnose(String deviceId, String ownerEmail) {
        Device device = findOwnedDevice(deviceId, ownerEmail);
        Instant checkedAt = Instant.now();
        List<DiagnosticCheck> checks = new ArrayList<>();
        String status = effectiveStatus(device, checkedAt);

        checks.add(connectivityCheck(device, status));

        CapabilityManifest manifest = CapabilityManifestAdapter.effectiveManifest(device);
        List<CapabilityDefinition> expectedMetrics = expectedMetrics(manifest);
        SensorReading latestReading = sensorReadingRepository
                .findTopByDeviceIdOrderByReceivedAtDesc(deviceId)
                .orElse(null);

        if (!expectedMetrics.isEmpty()) {
            checks.add(telemetryCheck(latestReading, checkedAt));
            checks.add(metricAvailabilityCheck(expectedMetrics, latestReading));
        }

        boolean supportsControl = manifest.getCapabilities().stream()
                .anyMatch(this::isSupportedControl);
        if (supportsControl) {
            checks.add(controlCheck(status));
        }

        List<Alert> unresolvedAlerts = alertRepository
                .findByDeviceIdAndOwnerEmailOrderByCreatedAtDesc(deviceId, normalize(ownerEmail))
                .stream()
                .filter(alert -> alert.getStatus() == AlertStatus.ACTIVE
                        || alert.getStatus() == AlertStatus.ACKNOWLEDGED)
                .toList();
        if (!unresolvedAlerts.isEmpty()) {
            checks.add(alertCheck(unresolvedAlerts));
        } else {
            checks.add(new DiagnosticCheck(
                    "alerts", "Recent alerts", DiagnosticCheckStatus.PASS,
                    "No unresolved alerts for this device.", checkedAt));
        }

        return new DeviceDiagnosticsResponse(
                device.getId(), device.getName(), checkedAt,
                overallStatus(checks), List.copyOf(checks));
    }

    public List<DeviceActivityResponse> activity(String deviceId, String ownerEmail) {
        Device device = findOwnedDevice(deviceId, ownerEmail);
        List<DeviceActivityResponse> events = new ArrayList<>();

        if (device.getCreatedAt() != null) {
            events.add(new DeviceActivityResponse(
                    "device-created-" + device.getId(), "DEVICE_CREATED",
                    "Device registered", "Device was added to your NexaIoT workspace.",
                    device.getCreatedAt(), device.getId()));
        }

        sensorReadingRepository.findTopByDeviceIdOrderByReceivedAtDesc(deviceId)
                .filter(reading -> reading.getReceivedAt() != null)
                .ifPresent(reading -> events.add(new DeviceActivityResponse(
                        "telemetry-" + reading.getId(), "TELEMETRY_RECEIVED",
                        "Latest telemetry received", "The device reported a telemetry reading.",
                        reading.getReceivedAt(), reading.getId())));

        alertRepository.findByDeviceIdAndOwnerEmailOrderByCreatedAtDesc(
                        deviceId, normalize(ownerEmail))
                .forEach(alert -> addAlertEvents(events, alert));

        return events.stream()
                .filter(event -> event.timestamp() != null)
                .sorted(Comparator.comparing(DeviceActivityResponse::timestamp).reversed())
                .limit(ACTIVITY_LIMIT)
                .toList();
    }

    private DiagnosticCheck connectivityCheck(Device device, String status) {
        String lastSeen = device.getLastSeenAt() == null
                ? "No device heartbeat has been received."
                : "Device is " + status.toLowerCase() + ". Last seen at " + device.getLastSeenAt() + ".";
        DiagnosticCheckStatus checkStatus = switch (status) {
            case "ONLINE" -> DiagnosticCheckStatus.PASS;
            case "MAINTENANCE" -> DiagnosticCheckStatus.WARNING;
            default -> DiagnosticCheckStatus.FAIL;
        };
        return new DiagnosticCheck("connectivity", "Device connectivity", checkStatus,
                lastSeen, device.getLastSeenAt());
    }

    private DiagnosticCheck telemetryCheck(SensorReading latestReading, Instant checkedAt) {
        if (latestReading == null || latestReading.getReceivedAt() == null) {
            return new DiagnosticCheck("telemetry", "Telemetry freshness",
                    DiagnosticCheckStatus.UNKNOWN,
                    "No telemetry reading is available for this device.", null);
        }

        long ageSeconds = Math.max(0, Duration.between(latestReading.getReceivedAt(), checkedAt).getSeconds());
        DiagnosticCheckStatus status = ageSeconds <= telemetryFreshnessThresholdSeconds
                ? DiagnosticCheckStatus.PASS
                : ageSeconds <= telemetryFreshnessThresholdSeconds * 2
                ? DiagnosticCheckStatus.WARNING
                : DiagnosticCheckStatus.FAIL;
        return new DiagnosticCheck("telemetry", "Telemetry freshness", status,
                "Latest telemetry was received " + ageSeconds + " seconds ago.", latestReading.getReceivedAt());
    }

    private DiagnosticCheck metricAvailabilityCheck(
            List<CapabilityDefinition> expectedMetrics,
            SensorReading latestReading
    ) {
        if (latestReading == null) {
            return new DiagnosticCheck("metrics", "Sensor/metric availability",
                    DiagnosticCheckStatus.UNKNOWN,
                    "Expected metrics cannot be checked until telemetry is received.", null);
        }

        List<String> missing = expectedMetrics.stream()
                .filter(definition -> valueFor(definition, latestReading) == null)
                .map(definition -> definition.getName() == null ? definition.getKey() : definition.getName())
                .toList();
        if (missing.isEmpty()) {
            return new DiagnosticCheck("metrics", "Sensor/metric availability",
                    DiagnosticCheckStatus.PASS,
                    "Latest telemetry contains all configured numeric metrics.", latestReading.getReceivedAt());
        }
        return new DiagnosticCheck("metrics", "Sensor/metric availability",
                DiagnosticCheckStatus.WARNING,
                "Latest telemetry is missing: " + String.join(", ", missing) + ".",
                latestReading.getReceivedAt());
    }

    private DiagnosticCheck controlCheck(String status) {
        DiagnosticCheckStatus checkStatus = "ONLINE".equals(status)
                ? DiagnosticCheckStatus.PASS : DiagnosticCheckStatus.WARNING;
        String message = "ONLINE".equals(status)
                ? "Supported power control is available while the device is online."
                : "Supported power control is unavailable while the device is " + status.toLowerCase() + ".";
        return new DiagnosticCheck("control", "Control availability", checkStatus, message, Instant.now());
    }

    private DiagnosticCheck alertCheck(List<Alert> unresolvedAlerts) {
        boolean critical = unresolvedAlerts.stream().anyMatch(alert -> alert.getSeverity() == AlertSeverity.CRITICAL);
        Alert latest = unresolvedAlerts.get(0);
        return new DiagnosticCheck("alerts", "Recent alerts",
                critical ? DiagnosticCheckStatus.FAIL : DiagnosticCheckStatus.WARNING,
                unresolvedAlerts.size() + " unresolved " + (critical ? "critical or other" : "warning/info") + " alert(s).",
                latest.getCreatedAt());
    }

    private DeviceHealthStatus overallStatus(List<DiagnosticCheck> checks) {
        if (checks.stream().anyMatch(check -> check.status() == DiagnosticCheckStatus.FAIL)) {
            return DeviceHealthStatus.CRITICAL;
        }
        if (checks.stream().anyMatch(check -> check.status() == DiagnosticCheckStatus.WARNING)) {
            return DeviceHealthStatus.WARNING;
        }
        if (checks.stream().anyMatch(check -> check.status() == DiagnosticCheckStatus.UNKNOWN)) {
            return DeviceHealthStatus.UNKNOWN;
        }
        return DeviceHealthStatus.HEALTHY;
    }

    private List<CapabilityDefinition> expectedMetrics(CapabilityManifest manifest) {
        return manifest.getCapabilities().stream()
                .filter(definition -> definition.getCategory() == CapabilityCategory.MEASUREMENT
                        || definition.getCategory() == CapabilityCategory.DEVICE_METRIC)
                .filter(definition -> definition.getDataType() == CapabilityDataType.NUMBER)
                .toList();
    }

    private boolean isSupportedControl(CapabilityDefinition definition) {
        return definition.getCategory() == CapabilityCategory.CONTROL
                && ("power".equals(definition.getKey())
                || "digitalOutput".equals(definition.getKey())
                || "power".equals(definition.getSemanticType())
                || "digitalOutput".equals(definition.getSemanticType()));
    }

    private Double valueFor(CapabilityDefinition definition, SensorReading reading) {
        if ("battery".equals(definition.getKey())) {
            return reading.getBattery() == null ? null : reading.getBattery().doubleValue();
        }
        if ("rssi".equals(definition.getKey())) {
            return reading.getRssi() == null ? null : reading.getRssi().doubleValue();
        }
        Map<String, Double> metrics = reading.getMetrics();
        return metrics == null ? null : metrics.get(definition.getKey());
    }

    private void addAlertEvents(List<DeviceActivityResponse> events, Alert alert) {
        if (alert.getCreatedAt() != null) {
            events.add(new DeviceActivityResponse(
                    "alert-created-" + alert.getId(), "ALERT_CREATED",
                    "Alert created", alert.getMessage() == null ? "A device alert was created." : alert.getMessage(),
                    alert.getCreatedAt(), alert.getId()));
        }
        if (alert.getAcknowledgedAt() != null) {
            events.add(new DeviceActivityResponse(
                    "alert-acknowledged-" + alert.getId(), "ALERT_ACKNOWLEDGED",
                    "Alert acknowledged", "An alert was acknowledged.", alert.getAcknowledgedAt(), alert.getId()));
        }
        if (alert.getResolvedAt() != null) {
            events.add(new DeviceActivityResponse(
                    "alert-resolved-" + alert.getId(), "ALERT_RESOLVED",
                    "Alert resolved", "An alert was resolved.", alert.getResolvedAt(), alert.getId()));
        }
    }

    private String effectiveStatus(Device device, Instant now) {
        if ("MAINTENANCE".equalsIgnoreCase(device.getStatus())) return "MAINTENANCE";
        return device.getLastSeenAt() != null
                && device.getLastSeenAt().isAfter(now.minusSeconds(ONLINE_TIMEOUT_SECONDS))
                ? "ONLINE" : "OFFLINE";
    }

    private Device findOwnedDevice(String deviceId, String ownerEmail) {
        return deviceRepository.findByIdAndOwnerEmail(deviceId, normalize(ownerEmail))
                .orElseThrow(() -> new DeviceNotFoundException("Device not found"));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
