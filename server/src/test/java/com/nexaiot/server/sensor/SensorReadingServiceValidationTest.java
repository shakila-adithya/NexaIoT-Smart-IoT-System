package com.nexaiot.server.sensor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.nexaiot.server.alert.AlertEvaluationService;
import com.nexaiot.server.alert.AlertRepository;
import com.nexaiot.server.alert.AlertRuleRepository;
import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityConstraints;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.device.CapabilityManifestSource;
import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.sensor.dto.CreateSensorReadingRequest;

class SensorReadingServiceValidationTest {

    @Test
    void declaredNumericMetricIsAccepted() {
        List<SensorReading> saved = new ArrayList<>();
        SensorReadingService service = service(deviceWith(manifest(metric("temperature", null))), saved);

        service.createReadingFromDeviceKey("NEXA-123", request(Map.of("temperature", 22.5), null, null));

        assertEquals(1, saved.size());
    }

    @Test
    void unknownMetricIsRejectedBeforePersistence() {
        List<SensorReading> saved = new ArrayList<>();
        SensorReadingService service = service(deviceWith(manifest(metric("temperature", null))), saved);

        assertThrows(IllegalArgumentException.class, () -> service.createReadingFromDeviceKey(
                "NEXA-123", request(Map.of("humidity", 60.0), null, null)));
        assertEquals(0, saved.size());
    }

    @Test
    void controlOnlyMetricIsRejected() {
        CapabilityDefinition control = metric("power", null);
        control.setCategory(CapabilityCategory.CONTROL);
        control.setDataType(CapabilityDataType.BOOLEAN);
        List<SensorReading> saved = new ArrayList<>();
        SensorReadingService service = service(deviceWith(manifest(control)), saved);

        assertThrows(IllegalArgumentException.class, () -> service.createReadingFromDeviceKey(
                "NEXA-123", request(Map.of("power", 1.0), null, null)));
        assertEquals(0, saved.size());
    }

    @Test
    void numericRangeIsEnforced() {
        CapabilityDefinition temperature = metric("temperature", null);
        CapabilityConstraints constraints = new CapabilityConstraints();
        constraints.setMin(-20.0);
        constraints.setMax(60.0);
        temperature.setConstraints(constraints);
        List<SensorReading> saved = new ArrayList<>();
        SensorReadingService service = service(deviceWith(manifest(temperature)), saved);

        assertThrows(IllegalArgumentException.class, () -> service.createReadingFromDeviceKey(
                "NEXA-123", request(Map.of("temperature", 80.0), null, null)));
        assertEquals(0, saved.size());
    }

    @Test
    void nonFiniteNumberIsRejected() {
        List<SensorReading> saved = new ArrayList<>();
        SensorReadingService service = service(deviceWith(manifest(metric("temperature", null))), saved);

        assertThrows(IllegalArgumentException.class, () -> service.createReadingFromDeviceKey(
                "NEXA-123", request(Map.of("temperature", Double.NaN), null, null)));
        assertEquals(0, saved.size());
    }

    @Test
    void declaredBatteryAndRssiMetricsAreAccepted() {
        List<SensorReading> saved = new ArrayList<>();
        SensorReadingService service = service(deviceWith(manifest(
                metric("battery", CapabilityCategory.DEVICE_METRIC),
                metric("rssi", CapabilityCategory.DEVICE_METRIC))), saved);

        service.createReadingFromDeviceKey("NEXA-123", request(null, 88, -52));

        assertEquals(1, saved.size());
    }

    @Test
    void undeclaredBatteryDoesNotUpdateDeviceOrPersist() {
        Device device = deviceWith(manifest(metric("temperature", null)));
        List<SensorReading> saved = new ArrayList<>();
        SensorReadingService service = service(device, saved);

        assertThrows(IllegalArgumentException.class, () -> service.createReadingFromDeviceKey(
                "NEXA-123", request(Map.of("temperature", 22.0), 88, null)));
        assertEquals(0, saved.size());
        org.junit.jupiter.api.Assertions.assertNull(device.getLastSeenAt());
    }

    @Test
    void legacyEffectiveManifestStillValidatesDeclaredMetrics() {
        Device device = new Device();
        device.setId("device-1");
        device.setDeviceKey("NEXA-123");
        com.nexaiot.server.device.DeviceCapabilities legacy = new com.nexaiot.server.device.DeviceCapabilities();
        legacy.setSensors(Set.of(com.nexaiot.server.device.SensorCapability.TEMPERATURE));
        device.setCapabilities(legacy);
        List<SensorReading> saved = new ArrayList<>();
        SensorReadingService service = service(device, saved);

        service.createReadingFromDeviceKey("NEXA-123", request(Map.of("temperature", 22.0), null, null));

        assertEquals(1, saved.size());
    }

    private SensorReadingService service(Device device, List<SensorReading> saved) {
        DeviceRepository devices = (DeviceRepository) Proxy.newProxyInstance(
                DeviceRepository.class.getClassLoader(), new Class[]{DeviceRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByDeviceKey" -> Optional.of(device);
                    case "save" -> args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        SensorReadingRepository readings = (SensorReadingRepository) Proxy.newProxyInstance(
                SensorReadingRepository.class.getClassLoader(), new Class[]{SensorReadingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> { SensorReading reading = (SensorReading) args[0]; saved.add(reading); yield reading; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        AlertRuleRepository rules = (AlertRuleRepository) Proxy.newProxyInstance(
                AlertRuleRepository.class.getClassLoader(), new Class[]{AlertRuleRepository.class},
                (proxy, method, args) -> method.getName().equals("findByDeviceIdAndOwnerEmailAndEnabledTrue") ? List.of() : null);
        AlertRepository alerts = (AlertRepository) Proxy.newProxyInstance(
                AlertRepository.class.getClassLoader(), new Class[]{AlertRepository.class},
                (proxy, method, args) -> method.getName().startsWith("findFirst") ? Optional.empty() : null);
        AlertEvaluationService evaluation = new AlertEvaluationService(alerts, rules, 100, 0, 10, 100, 0, -100);
        return new SensorReadingService(readings, devices, evaluation);
    }

    private Device deviceWith(CapabilityManifest manifest) {
        Device device = new Device();
        device.setId("device-1");
        device.setDeviceKey("NEXA-123");
        device.setOwnerEmail("owner@example.com");
        device.setCapabilityManifest(manifest);
        return device;
    }

    private CapabilityManifest manifest(CapabilityDefinition... definitions) {
        CapabilityManifest manifest = new CapabilityManifest(CapabilityManifestSource.CUSTOM);
        manifest.setCapabilities(List.of(definitions));
        return manifest;
    }

    private CapabilityDefinition metric(String key, CapabilityCategory category) {
        CapabilityDefinition definition = new CapabilityDefinition();
        definition.setKey(key);
        definition.setName(key);
        definition.setCategory(category == null ? CapabilityCategory.MEASUREMENT : category);
        definition.setDataType(CapabilityDataType.NUMBER);
        definition.setReadOnly(true);
        return definition;
    }

    private CreateSensorReadingRequest request(Map<String, Double> metrics, Integer battery, Integer rssi) {
        return new CreateSensorReadingRequest(metrics, battery, rssi, null);
    }
}
