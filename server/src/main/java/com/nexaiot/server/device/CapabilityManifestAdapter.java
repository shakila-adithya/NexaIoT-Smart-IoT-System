package com.nexaiot.server.device;

import java.util.ArrayList;
import java.util.List;

public final class CapabilityManifestAdapter {

    private CapabilityManifestAdapter() {
    }

    public static CapabilityManifest effectiveManifest(Device device) {
        if (device != null && CapabilityManifestValidator.isValid(device.getCapabilityManifest())) {
            return device.getCapabilityManifest();
        }

        if (device != null && device.getCapabilities() != null) {
            return fromLegacy(device.getCapabilities());
        }

        return emptyManifest();
    }

    public static CapabilityManifest fromLegacy(DeviceCapabilities legacy) {
        CapabilityManifest manifest = new CapabilityManifest(CapabilityManifestSource.LEGACY_ADAPTER);
        if (legacy == null) {
            return manifest;
        }

        List<CapabilityDefinition> definitions = new ArrayList<>();
        if (legacy.getSensors() != null) {
            legacy.getSensors().forEach(capability -> definitions.add(sensor(capability)));
        }
        if (legacy.getDeviceMetrics() != null) {
            legacy.getDeviceMetrics().forEach(capability -> definitions.add(deviceMetric(capability)));
        }
        if (legacy.getControls() != null) {
            legacy.getControls().forEach(capability -> definitions.add(control(capability)));
        }
        manifest.setCapabilities(definitions);
        return manifest;
    }

    public static CapabilityManifest emptyManifest() {
        return new CapabilityManifest(CapabilityManifestSource.LEGACY_ADAPTER);
    }

    private static CapabilityDefinition sensor(SensorCapability capability) {
        return measurement(switch (capability) {
            case TEMPERATURE -> definition("temperature", "Temperature", "°C", "temperature");
            case HUMIDITY -> definition("humidity", "Humidity", "%", "humidity");
            case SOIL_MOISTURE -> definition("soilMoisture", "Soil Moisture", "%", "soilMoisture");
            case PRESSURE -> definition("pressure", "Pressure", "hPa", "pressure");
            case LIGHT -> definition("light", "Light", "lux", "light");
            case AIR_QUALITY -> definition("airQuality", "Air Quality", "AQI", "airQuality");
        });
    }

    private static CapabilityDefinition deviceMetric(DeviceMetricCapability capability) {
        CapabilityDefinition definition = switch (capability) {
            case BATTERY -> definition("battery", "Battery", "%", "battery");
            case RSSI -> definition("rssi", "Signal Strength", "dBm", "rssi");
            case ENERGY_CONSUMPTION -> definition("energyConsumption", "Energy Consumption", "kWh", "energyConsumption");
        };
        definition.setCategory(CapabilityCategory.DEVICE_METRIC);
        definition.setReadOnly(true);
        definition.setChartable(true);
        definition.setValueKind(CapabilityValueKind.GAUGE);
        if (capability == DeviceMetricCapability.ENERGY_CONSUMPTION) {
            definition.setValueKind(CapabilityValueKind.COUNTER);
        }
        return definition;
    }

    private static CapabilityDefinition control(ControlCapability capability) {
        CapabilityDefinition definition = new CapabilityDefinition();
        definition.setCategory(CapabilityCategory.CONTROL);
        definition.setReadOnly(false);
        definition.setChartable(false);
        switch (capability) {
            case DIGITAL_OUTPUT -> {
                definition.setKey("digitalOutput");
                definition.setName("Digital Output");
                definition.setDataType(CapabilityDataType.BOOLEAN);
                definition.setSemanticType("digitalOutput");
                definition.setControl(new CapabilityControl(CapabilityControlType.SWITCH));
            }
            case TEMPERATURE_SETPOINT -> {
                definition.setKey("temperatureSetpoint");
                definition.setName("Temperature Set Point");
                definition.setDataType(CapabilityDataType.NUMBER);
                definition.setUnit("°C");
                definition.setSemanticType("temperatureSetpoint");
                definition.setControl(new CapabilityControl(CapabilityControlType.SLIDER));
            }
            case AUTO_MODE -> {
                definition.setKey("autoMode");
                definition.setName("Auto Mode");
                definition.setDataType(CapabilityDataType.BOOLEAN);
                definition.setSemanticType("autoMode");
                definition.setControl(new CapabilityControl(CapabilityControlType.SWITCH));
            }
            case ECO_SCHEDULE -> {
                definition.setKey("ecoSchedule");
                definition.setName("Eco Schedule");
                definition.setDataType(CapabilityDataType.STRING);
                definition.setSemanticType("ecoSchedule");
                definition.setControl(new CapabilityControl(CapabilityControlType.SELECT));
            }
        }
        return definition;
    }

    private static CapabilityDefinition definition(String key, String name, String unit, String semanticType) {
        CapabilityDefinition definition = new CapabilityDefinition();
        definition.setKey(key);
        definition.setName(name);
        definition.setDataType(CapabilityDataType.NUMBER);
        definition.setUnit(unit);
        definition.setSemanticType(semanticType);
        return definition;
    }

    private static CapabilityDefinition measurement(CapabilityDefinition definition) {
        definition.setCategory(CapabilityCategory.MEASUREMENT);
        definition.setValueKind(CapabilityValueKind.GAUGE);
        definition.setReadOnly(true);
        definition.setChartable(true);
        return definition;
    }
}
