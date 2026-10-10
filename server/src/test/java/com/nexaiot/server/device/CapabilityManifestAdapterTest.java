package com.nexaiot.server.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nexaiot.server.device.dto.DeviceResponse;

class CapabilityManifestAdapterTest {

    @Test
    void convertsLegacyTemperatureBatteryAndDigitalOutput() {
        DeviceCapabilities legacy = new DeviceCapabilities();
        legacy.setSensors(new LinkedHashSet<>(List.of(SensorCapability.TEMPERATURE)));
        legacy.setDeviceMetrics(new LinkedHashSet<>(List.of(DeviceMetricCapability.BATTERY)));
        legacy.setControls(new LinkedHashSet<>(List.of(ControlCapability.DIGITAL_OUTPUT)));

        CapabilityManifest manifest = CapabilityManifestAdapter.fromLegacy(legacy);

        assertEquals(CapabilityManifestSource.LEGACY_ADAPTER, manifest.getSource());
        assertEquals(List.of("temperature", "battery", "digitalOutput"),
                manifest.getCapabilities().stream().map(CapabilityDefinition::getKey).toList());
        assertEquals(CapabilityCategory.MEASUREMENT, manifest.getCapabilities().get(0).getCategory());
        assertEquals(CapabilityDataType.BOOLEAN, manifest.getCapabilities().get(2).getDataType());
        assertEquals(CapabilityControlType.SWITCH,
                manifest.getCapabilities().get(2).getControl().getType());
    }

    @Test
    void preservesLegacyDeviceMetricSemantics() {
        DeviceCapabilities legacy = new DeviceCapabilities();
        legacy.setDeviceMetrics(new LinkedHashSet<>(List.of(
                DeviceMetricCapability.BATTERY,
                DeviceMetricCapability.RSSI,
                DeviceMetricCapability.ENERGY_CONSUMPTION
        )));

        CapabilityManifest manifest = CapabilityManifestAdapter.fromLegacy(legacy);

        CapabilityDefinition battery = manifest.getCapabilities().get(0);
        assertMetric(battery, "battery", "%", "battery", CapabilityValueKind.GAUGE);

        CapabilityDefinition rssi = manifest.getCapabilities().get(1);
        assertMetric(rssi, "rssi", "dBm", "rssi", CapabilityValueKind.GAUGE);

        CapabilityDefinition energy = manifest.getCapabilities().get(2);
        assertMetric(energy, "energyConsumption", "kWh", "energyConsumption", CapabilityValueKind.COUNTER);
    }

    private void assertMetric(
            CapabilityDefinition definition,
            String key,
            String unit,
            String semanticType,
            CapabilityValueKind valueKind
    ) {
        assertEquals(key, definition.getKey());
        assertEquals(CapabilityCategory.DEVICE_METRIC, definition.getCategory());
        assertEquals(CapabilityDataType.NUMBER, definition.getDataType());
        assertEquals(unit, definition.getUnit());
        assertEquals(semanticType, definition.getSemanticType());
        assertTrue(definition.isReadOnly());
        assertTrue(definition.isChartable());
        assertEquals(valueKind, definition.getValueKind());
    }

    @Test
    void nullLegacyCapabilitiesBecomeEmptyManifest() {
        CapabilityManifest manifest = CapabilityManifestAdapter.fromLegacy(null);

        assertNotNull(manifest);
        assertTrue(manifest.getCapabilities().isEmpty());
        assertTrue(CapabilityManifestValidator.isValid(manifest));
    }

    @Test
    void effectiveManifestPrefersValidManifestWithoutPersistingLegacyAdaptation() {
        Device device = new Device();
        DeviceCapabilities legacy = new DeviceCapabilities();
        legacy.setSensors(new LinkedHashSet<>(List.of(SensorCapability.TEMPERATURE)));
        device.setCapabilities(legacy);

        CapabilityManifest explicit = new CapabilityManifest(CapabilityManifestSource.CUSTOM);
        CapabilityDefinition custom = new CapabilityDefinition();
        custom.setKey("customValue");
        custom.setName("Custom Value");
        custom.setCategory(CapabilityCategory.MEASUREMENT);
        custom.setDataType(CapabilityDataType.NUMBER);
        explicit.setCapabilities(List.of(custom));
        device.setCapabilityManifest(explicit);

        CapabilityManifest effective = CapabilityManifestAdapter.effectiveManifest(device);

        assertEquals("customValue", effective.getCapabilities().get(0).getKey());
        assertEquals(1, device.getCapabilities().getSensors().size());
    }

    @Test
    void invalidManifestsAreRejected() {
        CapabilityManifest duplicateKeys = new CapabilityManifest(CapabilityManifestSource.CUSTOM);
        CapabilityDefinition first = numeric("value", "Value");
        CapabilityDefinition second = numeric("value", "Other Value");
        duplicateKeys.setCapabilities(List.of(first, second));
        assertFalse(CapabilityManifestValidator.isValid(duplicateKeys));

        CapabilityConstraints constraints = new CapabilityConstraints();
        constraints.setMin(10.0);
        constraints.setMax(5.0);
        first.setConstraints(constraints);
        duplicateKeys.setCapabilities(List.of(first));
        assertFalse(CapabilityManifestValidator.isValid(duplicateKeys));
    }

    @Test
    void deviceResponseExposesCapabilityManifest() {
        CapabilityManifest manifest = CapabilityManifestAdapter.fromLegacy(null);

        DeviceResponse response = new DeviceResponse(
                "device-id",
                "NEXA-123",
                "Device",
                "Sensor",
                "Lab",
                "OFFLINE",
                false,
                null,
                null,
                null,
                null,
                manifest
        );

        assertEquals(manifest, response.getCapabilityManifest());
    }

    private CapabilityDefinition numeric(String key, String name) {
        CapabilityDefinition definition = new CapabilityDefinition();
        definition.setKey(key);
        definition.setName(name);
        definition.setCategory(CapabilityCategory.MEASUREMENT);
        definition.setDataType(CapabilityDataType.NUMBER);
        return definition;
    }
}
