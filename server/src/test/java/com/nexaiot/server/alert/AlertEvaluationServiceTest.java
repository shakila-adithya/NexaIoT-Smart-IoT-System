package com.nexaiot.server.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.device.CapabilityManifestSource;
import com.nexaiot.server.device.Device;
import com.nexaiot.server.sensor.SensorReading;

class AlertEvaluationServiceTest {

    @Test
    void createsCustomThresholdAlertOnceAndIgnoresDisabledRules() {
        Device device = device();
        AlertRule rule = new AlertRule();
        rule.setId("rule-1");
        rule.setOwnerEmail("owner@example.com");
        rule.setDeviceId(device.getId());
        rule.setRuleType(AlertRuleType.METRIC_THRESHOLD);
        rule.setMetricKey("temperature");
        rule.setOperator(AlertRuleOperator.GREATER_THAN);
        rule.setThreshold(35.0);
        rule.setSeverity(AlertSeverity.CRITICAL);
        rule.setName("Temperature high");
        rule.setEnabled(true);

        List<Alert> alerts = new ArrayList<>();
        AlertEvaluationService service = new AlertEvaluationService(
                alertRepository(alerts), ruleRepository(List.of(rule)), 30, 10, 20, 70, 40, -70
        );

        SensorReading reading = new SensorReading();
        reading.setMetrics(java.util.Map.of("temperature", 38.0));
        service.evaluate(device, reading);
        service.evaluate(device, reading);

        assertEquals(1, alerts.size());
        assertEquals(AlertType.CUSTOM_THRESHOLD, alerts.get(0).getType());
        assertEquals("rule-1", alerts.get(0).getAlertRuleId());
    }

    private Device device() {
        Device device = new Device();
        device.setId("device-1");
        device.setOwnerEmail("owner@example.com");
        device.setDeviceKey("NEXA-1");
        device.setName("Sensor");
        CapabilityManifest manifest = new CapabilityManifest(CapabilityManifestSource.CUSTOM);
        CapabilityDefinition definition = new CapabilityDefinition();
        definition.setKey("temperature");
        definition.setName("Temperature");
        definition.setCategory(CapabilityCategory.MEASUREMENT);
        definition.setDataType(CapabilityDataType.NUMBER);
        manifest.setCapabilities(List.of(definition));
        device.setCapabilityManifest(manifest);
        return device;
    }

    private AlertRuleRepository ruleRepository(List<AlertRule> rules) {
        return (AlertRuleRepository) Proxy.newProxyInstance(
                AlertRuleRepository.class.getClassLoader(), new Class[]{AlertRuleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByDeviceIdAndOwnerEmailAndEnabledTrue" -> rules;
                    case "toString" -> "rule-repository";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private AlertRepository alertRepository(List<Alert> alerts) {
        return (AlertRepository) Proxy.newProxyInstance(
                AlertRepository.class.getClassLoader(), new Class[]{AlertRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findFirstByDeviceIdAndOwnerEmailAndAlertRuleIdAndStatusIn" -> alerts.stream()
                            .filter(alert -> alert.getAlertRuleId().equals(args[2])
                                    && Set.of(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED).contains(alert.getStatus()))
                            .findFirst();
                    case "findFirstByDeviceIdAndOwnerEmailAndTypeAndStatusIn" -> Optional.empty();
                    case "save" -> { Alert alert = (Alert) args[0]; alerts.add(alert); yield alert; }
                    case "toString" -> "alert-repository";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
