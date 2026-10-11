package com.nexaiot.server.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import com.nexaiot.server.alert.Alert;
import com.nexaiot.server.alert.AlertRepository;
import com.nexaiot.server.alert.AlertSeverity;
import com.nexaiot.server.alert.AlertStatus;
import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.device.CapabilityManifestSource;
import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceCapabilities;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.device.SensorCapability;
import com.nexaiot.server.report.dto.GenerateReportRequest;
import com.nexaiot.server.sensor.SensorReading;
import com.nexaiot.server.sensor.SensorReadingRepository;

class GeneratedReportServiceTest {
    @Test
    void generatesOwnerScopedNumericSnapshotAndAlertSummary() {
        Device device = device("device-1", "owner@example.com");
        SensorReading first = reading(20.0, Instant.now().minusSeconds(20));
        SensorReading second = reading(30.0, Instant.now().minusSeconds(10));
        Alert alert = alert("alert-1", "owner@example.com", "device-1");
        List<GeneratedReport> saved = new ArrayList<>();
        GeneratedReportService service = service(device, List.of(first, second), List.of(alert), saved);

        GeneratedReportServiceTestRequest request = new GeneratedReportServiceTestRequest("device-1", "1h");
        var result = service.generate(request.value(), "OWNER@example.com");

        assertEquals("device-1", result.deviceId());
        assertEquals(2, result.telemetrySummary().get("temperature").getCount());
        assertEquals(20.0, result.telemetrySummary().get("temperature").getMinimum());
        assertEquals(30.0, result.telemetrySummary().get("temperature").getMaximum());
        assertEquals(25.0, result.telemetrySummary().get("temperature").getAverage());
        assertEquals(1, result.alertCount());
        assertEquals(1, result.severityCounts().get("CRITICAL"));
        assertEquals(1, result.telemetryTrend().size());
        assertEquals(25.0, result.telemetryTrend().get(0).getMetrics().get("temperature"));
        assertEquals(1, saved.size());
    }

    @Test
    void usesRangeSpecificTrendBuckets() {
        Instant now = Instant.now();
        for (String rangeAndOffset : List.of("1h:310", "6h:910", "24h:3610", "7d:21610")) {
            String[] parts = rangeAndOffset.split(":");
            List<SensorReading> readings = List.of(reading(20.0, now.minusSeconds(10)), reading(30.0, now.minusSeconds(Long.parseLong(parts[1]))));
            GeneratedReportService service = service(device("device-1", "owner@example.com"), readings, List.of(), new ArrayList<>());
            GenerateReportRequest request = new GenerateReportRequest(); request.setDeviceId("device-1"); request.setRange(parts[0]);
            assertEquals(2, service.generate(request, "owner@example.com").telemetryTrend().size(), parts[0]);
        }
    }

    @Test
    void supportsEmptyTelemetryAndRejectsInvalidOrUnownedRequests() {
        Device device = device("device-1", "owner@example.com");
        GeneratedReportService service = service(device, List.of(), List.of(), new ArrayList<>());
        GenerateReportRequest empty = new GenerateReportRequest(); empty.setDeviceId("device-1"); empty.setRange("7d");
        assertEquals(0, service.generate(empty, "owner@example.com").telemetrySummary().size());
        GenerateReportRequest invalid = new GenerateReportRequest(); invalid.setDeviceId("device-1"); invalid.setRange("2h");
        assertThrows(IllegalArgumentException.class, () -> service.generate(invalid, "owner@example.com"));
        GenerateReportRequest other = new GenerateReportRequest(); other.setDeviceId("device-1"); other.setRange("1h");
        assertThrows(RuntimeException.class, () -> service.generate(other, "other@example.com"));
    }

    @Test
    void reportsLegacyDeviceCapabilitiesWhenNativeManifestIsMissing() {
        Device device = legacyDevice("device-1", "owner@example.com");
        device.setStatus("OFFLINE");
        device.setLastSeenAt(Instant.now());
        SensorReading reading = reading(28.4, Instant.now().minusSeconds(10));
        GeneratedReportService service = service(device, List.of(reading), List.of(), new ArrayList<>());
        GenerateReportRequest request = new GenerateReportRequest();
        request.setDeviceId("device-1");
        request.setRange("1h");

        var result = service.generate(request, "owner@example.com");

        assertEquals(1, result.telemetrySummary().get("temperature").getCount());
        assertEquals(28.4, result.telemetryTrend().get(0).getMetrics().get("temperature"));
        assertEquals("ONLINE", result.deviceStatus());
    }

    private GeneratedReportService service(Device device, List<SensorReading> readings, List<Alert> alerts, List<GeneratedReport> saved) {
        DeviceRepository devices = (DeviceRepository) Proxy.newProxyInstance(DeviceRepository.class.getClassLoader(), new Class[]{DeviceRepository.class}, (proxy, method, args) -> "findByIdAndOwnerEmail".equals(method.getName()) && device.getId().equals(args[0]) && device.getOwnerEmail().equals(args[1]) ? Optional.of(device) : Optional.empty());
        SensorReadingRepository sensorReadings = (SensorReadingRepository) Proxy.newProxyInstance(SensorReadingRepository.class.getClassLoader(), new Class[]{SensorReadingRepository.class}, (proxy, method, args) -> "findByDeviceIdAndReceivedAtBetweenOrderByReceivedAtAsc".equals(method.getName()) ? readings : List.of());
        AlertRepository alertRepository = (AlertRepository) Proxy.newProxyInstance(AlertRepository.class.getClassLoader(), new Class[]{AlertRepository.class}, (proxy, method, args) -> "findByDeviceIdAndOwnerEmailAndCreatedAtBetweenOrderByCreatedAtAsc".equals(method.getName()) ? alerts : List.of());
        GeneratedReportRepository reports = (GeneratedReportRepository) Proxy.newProxyInstance(GeneratedReportRepository.class.getClassLoader(), new Class[]{GeneratedReportRepository.class}, (proxy, method, args) -> "save".equals(method.getName()) ? save((GeneratedReport) args[0], saved) : List.of());
        return new GeneratedReportService(reports, devices, sensorReadings, alertRepository);
    }
    private Object save(GeneratedReport report, List<GeneratedReport> saved) { report.setId("report-1"); saved.add(report); return report; }
    private Device device(String id, String owner) { Device device = new Device(); device.setId(id); device.setOwnerEmail(owner); device.setName("Sensor"); device.setStatus("ONLINE"); CapabilityManifest manifest = new CapabilityManifest(CapabilityManifestSource.CUSTOM); CapabilityDefinition definition = new CapabilityDefinition(); definition.setKey("temperature"); definition.setName("Temperature"); definition.setUnit("°C"); definition.setCategory(CapabilityCategory.MEASUREMENT); definition.setDataType(CapabilityDataType.NUMBER); manifest.setCapabilities(List.of(definition)); device.setCapabilityManifest(manifest); return device; }
    private Device legacyDevice(String id, String owner) { Device device = new Device(); device.setId(id); device.setOwnerEmail(owner); device.setName("Legacy Sensor"); device.setStatus("ONLINE"); DeviceCapabilities capabilities = new DeviceCapabilities(); capabilities.setSensors(java.util.Set.of(SensorCapability.TEMPERATURE)); device.setCapabilities(capabilities); return device; }
    private SensorReading reading(double temperature, Instant time) { SensorReading reading = new SensorReading(); reading.setDeviceId("device-1"); reading.setMetrics(java.util.Map.of("temperature", temperature)); reading.setReceivedAt(time); return reading; }
    private Alert alert(String id, String owner, String deviceId) { Alert alert = new Alert(); alert.setId(id); alert.setOwnerEmail(owner); alert.setDeviceId(deviceId); alert.setSeverity(AlertSeverity.CRITICAL); alert.setStatus(AlertStatus.ACTIVE); alert.setCreatedAt(Instant.now()); return alert; }
    private record GeneratedReportServiceTestRequest(String deviceId, String range) { GenerateReportRequest value() { GenerateReportRequest request = new GenerateReportRequest(); request.setDeviceId(deviceId); request.setRange(range); return request; } }
}
