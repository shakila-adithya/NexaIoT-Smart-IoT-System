package com.nexaiot.server.alert;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;

@Service
public class OfflineAlertMonitoringService {

    private static final Set<AlertStatus> UNRESOLVED_STATUSES = Set.of(
            AlertStatus.ACTIVE,
            AlertStatus.ACKNOWLEDGED
    );

    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final Duration offlineTimeout;

    public OfflineAlertMonitoringService(
            DeviceRepository deviceRepository,
            AlertRepository alertRepository,
            @Value("${alerts.device-offline-timeout-seconds}")
            long offlineTimeoutSeconds
    ) {
        this.deviceRepository = deviceRepository;
        this.alertRepository = alertRepository;
        this.offlineTimeout = Duration.ofSeconds(offlineTimeoutSeconds);
    }

    @Scheduled(fixedDelayString = "${alerts.offline-check-interval-ms}")
    public void monitorDevices() {
        Instant now = Instant.now();

        for (Device device : deviceRepository.findAll()) {
            try {
                evaluateDevice(device, now);
            } catch (RuntimeException ignored) {
                // Keep one device failure from stopping monitoring for the rest.
            }
        }
    }

    private void evaluateDevice(Device device, Instant now) {
        Instant lastSeenAt = device.getLastSeenAt();

        if (lastSeenAt == null) {
            return;
        }

        boolean offline = lastSeenAt.plus(offlineTimeout).isBefore(now);

        var existingAlert = alertRepository
                .findFirstByDeviceIdAndOwnerEmailAndTypeAndStatusIn(
                        device.getId(),
                        device.getOwnerEmail(),
                        AlertType.DEVICE_OFFLINE,
                        UNRESOLVED_STATUSES
                );

        if (offline) {
            if (existingAlert.isPresent()) {
                return;
            }

            Alert alert = new Alert();
            alert.setOwnerEmail(device.getOwnerEmail());
            alert.setDeviceId(device.getId());
            alert.setDeviceKey(device.getDeviceKey());
            alert.setDeviceName(device.getName());
            alert.setType(AlertType.DEVICE_OFFLINE);
            alert.setSeverity(AlertSeverity.WARNING);
            alert.setMessage("Device has stopped reporting telemetry.");
            alert.setThreshold(offlineTimeout.toSeconds() * 1.0);
            alert.setStatus(AlertStatus.ACTIVE);
            alert.setCreatedAt(now);

            alertRepository.save(alert);
            return;
        }

        existingAlert.ifPresent(alert -> {
            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(now);
            alertRepository.save(alert);
        });
    }
}
