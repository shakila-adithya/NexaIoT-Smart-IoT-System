package com.nexaiot.server.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.device.CapabilityManifestSource;
import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.sensor.SensorReading;
import com.nexaiot.server.sensor.SensorReadingRepository;

class EnergyAnalyticsServiceTest {

    @Test
    void ownerCanRequestPowerAnalyticsWithCorrectIntegrationAndSummary() {
        Instant now = Instant.now();
        Device device = deviceWith(capability("power", "Power", "W"));
        EnergyAnalyticsResponse result = service(device, List.of(
                reading(now.minusSeconds(20), Map.of("power", 100d)),
                reading(now.minusSeconds(10), Map.of("power", 200d))
        )).analyze("device-1", "owner@example.com", "1h");

        assertEquals("POWER_INTEGRATION", result.source());
        assertEquals(2, result.sampleCount());
        assertEquals(150d, result.averagePowerW(), 0.0001);
        assertEquals(200d, result.peakPowerW(), 0.0001);
        assertEquals(150d * 10d / 3600d / 1000d, result.totalEnergyKWh(), 0.000001);
        assertEquals(1, result.trend().size());
    }

    @Test
    void foreignDeviceIsRejected() {
        assertThrows(RuntimeException.class, () -> service(
                deviceWith(capability("power", "Power", "W")), List.of())
                .analyze("device-1", "other@example.com", "24h"));
    }

    @Test
    void temperatureOnlyDeviceReturnsUnavailable() {
        EnergyAnalyticsResponse result = service(
                deviceWith(capability("temperature", "Temperature", "°C")), List.of())
                .analyze("device-1", "owner@example.com", "24h");

        assertFalse(result.available());
        assertEquals("UNAVAILABLE", result.source());
        assertEquals(0, result.trend().size());
    }

    @Test
    void missingPowerSamplesAreNotTreatedAsZero() {
        Instant now = Instant.now();
        Device device = deviceWith(capability("power", "Power", "W"));
        EnergyAnalyticsResponse result = service(device, List.of(
                reading(now.minusSeconds(30), Map.of("power", 100d)),
                reading(now.minusSeconds(20), Map.of()),
                reading(now.minusSeconds(10), Map.of("power", 100d))
        )).analyze("device-1", "owner@example.com", "1h");

        assertEquals(100d, result.averagePowerW(), 0.0001);
        assertEquals(100d * 20d / 3600d / 1000d, result.totalEnergyKWh(), 0.000001);
    }

    @Test
    void outOfOrderSamplesAreSortedBeforeIntegration() {
        Instant now = Instant.now();
        Device device = deviceWith(capability("power", "Power", "W"));
        EnergyAnalyticsResponse result = service(device, List.of(
                reading(now.minusSeconds(10), Map.of("power", 200d)),
                reading(now.minusSeconds(20), Map.of("power", 100d))
        )).analyze("device-1", "owner@example.com", "1h");

        assertEquals(150d * 10d / 3600d / 1000d, result.totalEnergyKWh(), 0.000001);
    }

    @Test
    void largeTelemetryGapIsNotIntegrated() {
        Instant now = Instant.now();
        Device device = deviceWith(capability("power", "Power", "W"));
        EnergyAnalyticsResponse result = service(device, List.of(
                reading(now.minusSeconds(3600), Map.of("power", 100d)),
                reading(now.minusSeconds(1), Map.of("power", 100d))
        )).analyze("device-1", "owner@example.com", "1h");

        assertEquals(0d, result.totalEnergyKWh(), 0.000001);
        assertEquals(0d, result.coverage(), 0.000001);
    }

    @Test
    void wattHoursAreConvertedToKilowattHoursForDirectEnergy() {
        Instant now = Instant.now();
        Device device = deviceWith(capability("energyConsumption", "Energy Consumption", "Wh"));
        EnergyAnalyticsResponse result = service(device, List.of(
                reading(now.minusSeconds(20), Map.of("energyConsumption", 1000d)),
                reading(now.minusSeconds(10), Map.of("energyConsumption", 1250d))
        )).analyze("device-1", "owner@example.com", "1h");

        assertEquals("DIRECT_ENERGY", result.source());
        assertEquals(0.25d, result.totalEnergyKWh(), 0.000001);
        assertEquals("kWh", result.unit());
    }

    @Test
    void decreasingCumulativeEnergyDoesNotCreateNegativeConsumption() {
        Instant now = Instant.now();
        Device device = deviceWith(capability("energyConsumption", "Energy Consumption", "kWh"));
        EnergyAnalyticsResponse result = service(device, List.of(
                reading(now.minusSeconds(20), Map.of("energyConsumption", 4d)),
                reading(now.minusSeconds(10), Map.of("energyConsumption", 3d))
        )).analyze("device-1", "owner@example.com", "1h");

        assertFalse(result.available());
        assertEquals("UNAVAILABLE", result.source());
    }

    @Test
    void booleanPowerControlAloneDoesNotEnableEnergyAnalytics() {
        CapabilityDefinition control = capability("power", "Power", "");
        control.setCategory(CapabilityCategory.CONTROL);
        control.setDataType(CapabilityDataType.BOOLEAN);
        EnergyAnalyticsResponse result = service(deviceWith(control), List.of())
                .analyze("device-1", "owner@example.com", "24h");

        assertFalse(result.available());
    }

    @Test
    void voltageAndCurrentProducePowerAnalyticsWhenBothAreDeclared() {
        Instant now = Instant.now();
        Device device = deviceWith(
                capability("voltage", "Voltage", "V"),
                capability("current", "Current", "A"));
        EnergyAnalyticsResponse result = service(device, List.of(
                reading(now.minusSeconds(20), Map.of("voltage", 10d, "current", 2d)),
                reading(now.minusSeconds(10), Map.of("voltage", 10d, "current", 3d))
        )).analyze("device-1", "owner@example.com", "1h");

        assertEquals("VOLTAGE_CURRENT", result.source());
        assertEquals(25d, result.averagePowerW(), 0.0001);
        assertEquals(30d, result.peakPowerW(), 0.0001);
    }

    @Test
    void voltageOnlyDoesNotEnableEnergyAnalytics() {
        EnergyAnalyticsResponse result = service(
                deviceWith(capability("voltage", "Voltage", "V")), List.of())
                .analyze("device-1", "owner@example.com", "24h");

        assertFalse(result.available());
        assertEquals("UNAVAILABLE", result.source());
    }

    @Test
    void currentOnlyDoesNotEnableEnergyAnalytics() {
        EnergyAnalyticsResponse result = service(
                deviceWith(capability("current", "Current", "A")), List.of())
                .analyze("device-1", "owner@example.com", "24h");

        assertFalse(result.available());
        assertEquals("UNAVAILABLE", result.source());
    }

    @Test
    void directCumulativeEnergyUsesMeterDeltaAndMeterSpanCoverage() {
        Instant now = Instant.now();
        Device device = deviceWith(capability("energyConsumption", "Energy Consumption", "kWh"));
        EnergyAnalyticsResponse result = service(device, List.of(
                reading(now.minusSeconds(2400), Map.of("energyConsumption", 10.2d)),
                reading(now.minusSeconds(1800), Map.of("energyConsumption", 10.4d)),
                reading(now.minusSeconds(1200), Map.of("energyConsumption", 10.7d))
        )).analyze("device-1", "owner@example.com", "1h");

        assertEquals(0.5d, result.totalEnergyKWh(), 0.000001);
        assertEquals("METER_SPAN", result.coverageBasis());
        assertTrue(result.coverage() > 0.3d && result.coverage() < 0.4d);
        assertEquals(null, result.averagePowerW());
        assertEquals(null, result.peakPowerW());
    }

    @Test
    void duplicatePowerTimestampsDoNotCreateAnExtraIntegrationInterval() {
        Instant now = Instant.now();
        Device device = deviceWith(capability("power", "Power", "W"));
        EnergyAnalyticsResponse result = service(device, List.of(
                reading(now.minusSeconds(20), Map.of("power", 100d)),
                reading(now.minusSeconds(20), Map.of("power", 100d)),
                reading(now.minusSeconds(10), Map.of("power", 100d))
        )).analyze("device-1", "owner@example.com", "1h");

        assertEquals(100d * 10d / 3600d / 1000d, result.totalEnergyKWh(), 0.000001);
    }

    private EnergyAnalyticsService service(Device device, List<SensorReading> readings) {
        DeviceRepository devices = (DeviceRepository) Proxy.newProxyInstance(
                DeviceRepository.class.getClassLoader(), new Class[]{DeviceRepository.class},
                (proxy, method, args) -> method.getName().equals("findByIdAndOwnerEmail")
                        && device.getOwnerEmail().equals(args[1])
                        ? Optional.of(device) : Optional.empty());
        SensorReadingRepository repository = (SensorReadingRepository) Proxy.newProxyInstance(
                SensorReadingRepository.class.getClassLoader(), new Class[]{SensorReadingRepository.class},
                (proxy, method, args) -> method.getName().equals("findByDeviceIdAndReceivedAtBetweenOrderByReceivedAtAsc")
                        ? readings : List.of());
        return new EnergyAnalyticsService(devices, repository);
    }

    private Device deviceWith(CapabilityDefinition... definitions) {
        Device device = new Device();
        device.setId("device-1");
        device.setOwnerEmail("owner@example.com");
        CapabilityManifest manifest = new CapabilityManifest(CapabilityManifestSource.CUSTOM);
        manifest.setCapabilities(List.of(definitions));
        device.setCapabilityManifest(manifest);
        return device;
    }

    private CapabilityDefinition capability(String key, String name, String unit) {
        CapabilityDefinition definition = new CapabilityDefinition();
        definition.setKey(key);
        definition.setName(name);
        definition.setUnit(unit);
        definition.setSemanticType(key);
        definition.setCategory(CapabilityCategory.MEASUREMENT);
        definition.setDataType(CapabilityDataType.NUMBER);
        return definition;
    }

    private SensorReading reading(Instant receivedAt, Map<String, Double> metrics) {
        SensorReading reading = new SensorReading();
        reading.setDeviceId("device-1");
        reading.setReceivedAt(receivedAt);
        reading.setMetrics(metrics);
        return reading;
    }
}
