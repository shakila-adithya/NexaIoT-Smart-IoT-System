package com.nexaiot.server.alert;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;

@Service
@Profile("!test")
public class OfflineAlertMonitoringService {

    private static final Set<AlertStatus> UNRESOLVED_STATUSES = Set.of(
            AlertStatus.ACTIVE,
            AlertStatus.ACKNOWLEDGED
    );

    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final Duration offlineTimeout;

    public OfflineAlertMonitoringService(
            DeviceRepository deviceRepository,
            AlertRepository alertRepository,
            AlertRuleRepository alertRuleRepository,
            @Value("${alerts.device-offline-timeout-seconds}")
            long offlineTimeoutSeconds
    ) {
        this.deviceRepository = deviceRepository;
        this.alertRepository = alertRepository;
        this.alertRuleRepository = alertRuleRepository;
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

        var customRules = alertRuleRepository
                .findByDeviceIdAndOwnerEmailAndRuleTypeAndEnabledTrue(
                        device.getId(), device.getOwnerEmail(), AlertRuleType.DEVICE_OFFLINE);

        if (!customRules.isEmpty()) {
            customRules.forEach(rule -> evaluateOfflineRule(device, rule, lastSeenAt, now));
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

    private void evaluateOfflineRule(Device device, AlertRule rule, Instant lastSeenAt, Instant now) {
        Duration timeout = Duration.ofMinutes(rule.getOfflineDurationMinutes());
        boolean offline = lastSeenAt.plus(timeout).isBefore(now);
        var existingAlert = alertRepository
                .findFirstByDeviceIdAndOwnerEmailAndAlertRuleIdAndStatusIn(
                        device.getId(), device.getOwnerEmail(), rule.getId(), UNRESOLVED_STATUSES);

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
            alert.setAlertRuleId(rule.getId());
            alert.setSeverity(rule.getSeverity());
            alert.setMessage("Alert rule \"" + rule.getName() + "\" detected an offline device.");
            alert.setThreshold(timeout.toSeconds() * 1.0);
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
