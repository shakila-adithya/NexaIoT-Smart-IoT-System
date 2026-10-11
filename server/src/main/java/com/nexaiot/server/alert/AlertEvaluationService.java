package com.nexaiot.server.alert;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifestAdapter;
import com.nexaiot.server.sensor.SensorReading;

@Service
public class AlertEvaluationService {

    private static final Set<AlertStatus> UNRESOLVED_STATUSES = Set.of(
            AlertStatus.ACTIVE,
            AlertStatus.ACKNOWLEDGED
    );

    private final AlertRepository alertRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final double temperatureHighThreshold;
    private final double temperatureLowThreshold;
    private final double batteryLowThreshold;
    private final double humidityHighThreshold;
    private final double humidityLowThreshold;
    private final double rssiWeakThreshold;

    public AlertEvaluationService(
            AlertRepository alertRepository,
            AlertRuleRepository alertRuleRepository,
            @Value("${alerts.temperature-high-threshold}")
            double temperatureHighThreshold,
            @Value("${alerts.temperature-low-threshold}")
            double temperatureLowThreshold,
            @Value("${alerts.battery-low-threshold}")
            double batteryLowThreshold,
            @Value("${alerts.humidity-high-threshold}")
            double humidityHighThreshold,
            @Value("${alerts.humidity-low-threshold}")
            double humidityLowThreshold,
            @Value("${alerts.rssi-weak-threshold}")
            double rssiWeakThreshold
    ) {
        this.alertRepository = alertRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.temperatureHighThreshold = temperatureHighThreshold;
        this.temperatureLowThreshold = temperatureLowThreshold;
        this.batteryLowThreshold = batteryLowThreshold;
        this.humidityHighThreshold = humidityHighThreshold;
        this.humidityLowThreshold = humidityLowThreshold;
        this.rssiWeakThreshold = rssiWeakThreshold;
    }

    public void evaluate(Device device, SensorReading reading) {
        Map<String, Double> metrics = reading.getMetrics();
        var enabledRules = alertRuleRepository.findByDeviceIdAndOwnerEmailAndEnabledTrue(
                device.getId(), device.getOwnerEmail());
        var customMetricKeys = enabledRules.stream()
                .filter(rule -> rule.getRuleType() == AlertRuleType.METRIC_THRESHOLD)
                .map(AlertRule::getMetricKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(java.util.stream.Collectors.toSet());

        evaluateCustomMetricRules(device, reading, enabledRules);

        if (metrics != null) {
            Double temperature = metrics.get("temperature");

            if (temperature != null && !customMetricKeys.contains("temperature")) {
                evaluateThresholdAlert(
                        device,
                        AlertType.TEMPERATURE_HIGH,
                        AlertSeverity.WARNING,
                        "Temperature exceeded the configured threshold.",
                        temperature,
                        temperatureHighThreshold,
                        temperature > temperatureHighThreshold
                );

                evaluateThresholdAlert(
                        device,
                        AlertType.TEMPERATURE_LOW,
                        AlertSeverity.WARNING,
                        "Temperature is below the configured threshold.",
                        temperature,
                        temperatureLowThreshold,
                        temperature < temperatureLowThreshold
                );
            }

            Double humidity = metrics.get("humidity");

            if (humidity != null && !customMetricKeys.contains("humidity")) {
                evaluateThresholdAlert(
                        device,
                        AlertType.HUMIDITY_HIGH,
                        AlertSeverity.WARNING,
                        "Humidity is above the configured threshold.",
                        humidity,
                        humidityHighThreshold,
                        humidity > humidityHighThreshold
                );

                evaluateThresholdAlert(
                        device,
                        AlertType.HUMIDITY_LOW,
                        AlertSeverity.WARNING,
                        "Humidity is below the configured threshold.",
                        humidity,
                        humidityLowThreshold,
                        humidity < humidityLowThreshold
                );
            }
        }

        Integer battery = reading.getBattery();

        if (battery != null && !customMetricKeys.contains("battery")) {
            double batteryValue = battery.doubleValue();

            evaluateThresholdAlert(
                    device,
                    AlertType.BATTERY_LOW,
                    AlertSeverity.WARNING,
                    "Battery level is below the configured threshold.",
                    batteryValue,
                    batteryLowThreshold,
                    battery < batteryLowThreshold
            );
        }

        Integer rssi = reading.getRssi();

        if (rssi != null && !customMetricKeys.contains("rssi")) {
            double rssiValue = rssi.doubleValue();

            evaluateThresholdAlert(
                    device,
                    AlertType.RSSI_WEAK,
                    AlertSeverity.WARNING,
                    "Signal strength is below the configured threshold.",
                    rssiValue,
                    rssiWeakThreshold,
                    rssi < rssiWeakThreshold
            );
        }
    }

    private void evaluateCustomMetricRules(
            Device device,
            SensorReading reading,
            java.util.List<AlertRule> enabledRules
    ) {
        var manifest = CapabilityManifestAdapter.effectiveManifest(device);
        for (AlertRule rule : enabledRules) {
            if (rule.getRuleType() != AlertRuleType.METRIC_THRESHOLD) {
                continue;
            }

            CapabilityDefinition definition = manifest.getCapabilities().stream()
                    .filter(capability -> rule.getMetricKey().equals(capability.getKey()))
                    .findFirst().orElse(null);
            if (definition == null || definition.getCategory() == CapabilityCategory.CONTROL
                    || definition.getDataType() != CapabilityDataType.NUMBER) {
                continue;
            }

            Double value = switch (definition.getKey()) {
                case "battery" -> reading.getBattery() == null ? null : reading.getBattery().doubleValue();
                case "rssi" -> reading.getRssi() == null ? null : reading.getRssi().doubleValue();
                default -> reading.getMetrics() == null ? null : reading.getMetrics().get(definition.getKey());
            };
            if (value == null) {
                continue;
            }

            boolean breached = switch (rule.getOperator()) {
                case GREATER_THAN -> value > rule.getThreshold();
                case GREATER_THAN_OR_EQUAL -> value >= rule.getThreshold();
                case LESS_THAN -> value < rule.getThreshold();
                case LESS_THAN_OR_EQUAL -> value <= rule.getThreshold();
            };
            evaluateRuleAlert(device, rule, value, breached);
        }
    }

    private void evaluateRuleAlert(Device device, AlertRule rule, double value, boolean breached) {
        var existingAlert = alertRepository
                .findFirstByDeviceIdAndOwnerEmailAndAlertRuleIdAndStatusIn(
                        device.getId(), device.getOwnerEmail(), rule.getId(), UNRESOLVED_STATUSES);
        if (!breached || existingAlert.isPresent()) {
            return;
        }

        Alert alert = new Alert();
        alert.setOwnerEmail(device.getOwnerEmail());
        alert.setDeviceId(device.getId());
        alert.setDeviceKey(device.getDeviceKey());
        alert.setDeviceName(device.getName());
        alert.setType(AlertType.CUSTOM_THRESHOLD);
        alert.setAlertRuleId(rule.getId());
        alert.setSeverity(rule.getSeverity());
        alert.setMessage("Alert rule \"" + rule.getName() + "\" threshold was exceeded.");
        alert.setValue(value);
        alert.setThreshold(rule.getThreshold());
        alert.setStatus(AlertStatus.ACTIVE);
        alert.setCreatedAt(Instant.now());
        alertRepository.save(alert);
    }

    private void evaluateThresholdAlert(
            Device device,
            AlertType type,
            AlertSeverity severity,
            String message,
            Double value,
            double threshold,
            boolean thresholdExceeded
    ) {
        var existingAlert = alertRepository
                .findFirstByDeviceIdAndOwnerEmailAndTypeAndStatusIn(
                        device.getId(),
                        device.getOwnerEmail(),
                        type,
                        UNRESOLVED_STATUSES
                );

        if (thresholdExceeded) {
            if (existingAlert.isPresent()) {
                return;
            }

            Alert alert = new Alert();
            alert.setOwnerEmail(device.getOwnerEmail());
            alert.setDeviceId(device.getId());
            alert.setDeviceKey(device.getDeviceKey());
            alert.setDeviceName(device.getName());
            alert.setType(type);
            alert.setSeverity(severity);
            alert.setMessage(message);
            alert.setValue(value);
            alert.setThreshold(threshold);
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
