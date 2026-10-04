package com.nexaiot.server.alert;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.sensor.SensorReading;

@Service
public class AlertEvaluationService {

    private static final Set<AlertStatus> UNRESOLVED_STATUSES = Set.of(
            AlertStatus.ACTIVE,
            AlertStatus.ACKNOWLEDGED
    );

    private final AlertRepository alertRepository;
    private final double temperatureHighThreshold;

    public AlertEvaluationService(
            AlertRepository alertRepository,
            @Value("${alerts.temperature-high-threshold}")
            double temperatureHighThreshold
    ) {
        this.alertRepository = alertRepository;
        this.temperatureHighThreshold = temperatureHighThreshold;
    }

    public void evaluate(Device device, SensorReading reading) {
        Map<String, Double> metrics = reading.getMetrics();

        if (metrics == null || metrics.get("temperature") == null) {
            return;
        }

        Double temperature = metrics.get("temperature");

        var existingAlert = alertRepository
                .findFirstByDeviceIdAndOwnerEmailAndTypeAndStatusIn(
                        device.getId(),
                        device.getOwnerEmail(),
                        AlertType.TEMPERATURE_HIGH,
                        UNRESOLVED_STATUSES
                );

        if (temperature > temperatureHighThreshold) {
            if (existingAlert.isPresent()) {
                return;
            }

            Alert alert = new Alert();
            alert.setOwnerEmail(device.getOwnerEmail());
            alert.setDeviceId(device.getId());
            alert.setDeviceKey(device.getDeviceKey());
            alert.setDeviceName(device.getName());
            alert.setType(AlertType.TEMPERATURE_HIGH);
            alert.setSeverity(AlertSeverity.WARNING);
            alert.setMessage(
                    "Temperature exceeded the configured threshold."
            );
            alert.setValue(temperature);
            alert.setThreshold(temperatureHighThreshold);
            alert.setStatus(AlertStatus.ACTIVE);
            alert.setCreatedAt(Instant.now());

            alertRepository.save(alert);
            return;
        }

        existingAlert.ifPresent(alert -> {
            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(Instant.now());
            alertRepository.save(alert);
        });
    }
}
