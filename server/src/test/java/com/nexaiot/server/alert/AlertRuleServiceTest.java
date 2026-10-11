package com.nexaiot.server.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.device.CapabilityManifestSource;
import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.alert.dto.AlertRuleRequest;

class AlertRuleServiceTest {

    @Test
    void createsOnlyForOwnedNumericCapability() {
        Device device = device("device-1", "owner@example.com");
        List<AlertRule> saved = new ArrayList<>();
        AlertRuleService service = new AlertRuleService(
                rulesRepository(saved, List.of()), devicesRepository(device)
        );

        AlertRuleRequest request = request("device-1", "temperature");
        AlertRule result = service.createRule(request, "OWNER@example.com") == null ? null : saved.get(0);

        assertEquals("owner@example.com", result.getOwnerEmail());
        assertEquals("temperature", result.getMetricKey());
        assertThrows(RuntimeException.class, () -> service.createRule(request("device-1", "missing"), "owner@example.com"));
        assertThrows(RuntimeException.class, () -> service.createRule(request("device-1", "temperature"), "other@example.com"));
    }

    @Test
    void metricThresholdRequiresAFiniteValueButAcceptsZeroAndNegativeValues() {
        Device device = device("device-1", "owner@example.com");
        List<AlertRule> saved = new ArrayList<>();
        AlertRuleService service = new AlertRuleService(
                rulesRepository(saved, List.of()), devicesRepository(device)
        );

        AlertRuleRequest missing = request("device-1", "temperature");
        missing.setThreshold(null);
        assertThrows(IllegalArgumentException.class, () -> service.createRule(missing, "owner@example.com"));

        AlertRuleRequest zero = request("device-1", "temperature");
        zero.setThreshold(0.0);
        assertDoesNotThrow(() -> service.createRule(zero, "owner@example.com"));

        AlertRuleRequest negative = request("device-1", "temperature");
        negative.setThreshold(-5.0);
        assertDoesNotThrow(() -> service.createRule(negative, "owner@example.com"));
    }

    @Test
    void offlineRuleRequiresPositiveDurationButDoesNotRequireMetricThreshold() {
        Device device = device("device-1", "owner@example.com");
        List<AlertRule> saved = new ArrayList<>();
        AlertRuleService service = new AlertRuleService(
                rulesRepository(saved, List.of()), devicesRepository(device)
        );

        AlertRuleRequest valid = offlineRequest("device-1", 5);
        assertDoesNotThrow(() -> service.createRule(valid, "owner@example.com"));

        AlertRuleRequest missing = offlineRequest("device-1", null);
        assertThrows(IllegalArgumentException.class, () -> service.createRule(missing, "owner@example.com"));

        AlertRuleRequest zero = offlineRequest("device-1", 0);
        assertThrows(IllegalArgumentException.class, () -> service.createRule(zero, "owner@example.com"));

        AlertRuleRequest negative = offlineRequest("device-1", -5);
        assertThrows(IllegalArgumentException.class, () -> service.createRule(negative, "owner@example.com"));
    }

    private AlertRuleRequest request(String deviceId, String metricKey) {
        AlertRuleRequest request = new AlertRuleRequest();
        request.setName("Temperature high");
        request.setDeviceId(deviceId);
        request.setRuleType(AlertRuleType.METRIC_THRESHOLD);
        request.setMetricKey(metricKey);
        request.setOperator(AlertRuleOperator.GREATER_THAN);
        request.setThreshold(35.0);
        request.setSeverity(AlertSeverity.CRITICAL);
        return request;
    }

    private AlertRuleRequest offlineRequest(String deviceId, Integer durationMinutes) {
        AlertRuleRequest request = new AlertRuleRequest();
        request.setName("Device offline");
        request.setDeviceId(deviceId);
        request.setRuleType(AlertRuleType.DEVICE_OFFLINE);
        request.setOfflineDurationMinutes(durationMinutes);
        request.setSeverity(AlertSeverity.WARNING);
        return request;
    }

    private Device device(String id, String owner) {
        Device device = new Device();
        device.setId(id);
        device.setOwnerEmail(owner);
        CapabilityManifest manifest = new CapabilityManifest(CapabilityManifestSource.CUSTOM);
        CapabilityDefinition temperature = new CapabilityDefinition();
        temperature.setKey("temperature");
        temperature.setName("Temperature");
        temperature.setCategory(CapabilityCategory.MEASUREMENT);
        temperature.setDataType(CapabilityDataType.NUMBER);
        manifest.setCapabilities(List.of(temperature));
        device.setCapabilityManifest(manifest);
        return device;
    }

    private DeviceRepository devicesRepository(Device device) {
        return (DeviceRepository) Proxy.newProxyInstance(
                DeviceRepository.class.getClassLoader(), new Class[]{DeviceRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndOwnerEmail" -> Optional.ofNullable(
                            device.getOwnerEmail().equals(args[1]) && device.getId().equals(args[0]) ? device : null);
                    case "toString" -> "device-repository";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private AlertRuleRepository rulesRepository(List<AlertRule> saved, List<AlertRule> existing) {
        return (AlertRuleRepository) Proxy.newProxyInstance(
                AlertRuleRepository.class.getClassLoader(), new Class[]{AlertRuleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> { AlertRule rule = (AlertRule) args[0]; rule.setId("rule-1"); saved.add(rule); yield rule; }
                    case "findByIdAndOwnerEmail" -> existing.stream().findFirst();
                    case "toString" -> "rule-repository";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
