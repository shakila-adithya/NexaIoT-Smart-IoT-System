package com.nexaiot.server.device.diagnostics;

import com.nexaiot.server.alert.AlertRepository;
import com.nexaiot.server.alert.Alert;
import com.nexaiot.server.alert.AlertSeverity;
import com.nexaiot.server.alert.AlertStatus;
import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.CapabilityControl;
import com.nexaiot.server.device.CapabilityControlType;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.device.CapabilityManifestSource;
import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.sensor.SensorReading;
import com.nexaiot.server.sensor.SensorReadingRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DeviceDiagnosticsServiceTest {

    @Test
    void onlineSensorReportsFreshTelemetryAndHealthyResult() {
        Device device = device("owner@example.com");
        CapabilityDefinition temperature = new CapabilityDefinition();
        temperature.setKey("temperature");
        temperature.setName("Temperature");
        temperature.setCategory(CapabilityCategory.MEASUREMENT);
        temperature.setDataType(CapabilityDataType.NUMBER);
        CapabilityManifest manifest = new CapabilityManifest(CapabilityManifestSource.CUSTOM);
        manifest.setCapabilities(List.of(temperature));
        device.setCapabilityManifest(manifest);

        SensorReading reading = new SensorReading();
        reading.setId("reading-1");
        reading.setReceivedAt(Instant.now());
        reading.setMetrics(Map.of("temperature", 28.0));

        DeviceDiagnosticsService service = service(device, reading, List.of());
        DeviceDiagnosticsResponse result = service.diagnose("device-1", "owner@example.com");

        assertEquals(DeviceHealthStatus.HEALTHY, result.overallStatus());
        assertTrue(result.checks().stream().anyMatch(check -> check.key().equals("telemetry")
                && check.status() == DiagnosticCheckStatus.PASS));
        assertTrue(result.checks().stream().anyMatch(check -> check.key().equals("metrics")
                && check.status() == DiagnosticCheckStatus.PASS));
    }

    @Test
    void actuatorOnlyDeviceDoesNotGetTelemetryFailureForMissingReadings() {
        Device device = device("owner@example.com");
        CapabilityDefinition power = new CapabilityDefinition();
        power.setKey("power");
        power.setName("Power");
        power.setCategory(CapabilityCategory.CONTROL);
        power.setDataType(CapabilityDataType.BOOLEAN);
        power.setReadOnly(false);
        power.setChartable(false);
        power.setControl(new CapabilityControl(CapabilityControlType.SWITCH));
        CapabilityManifest manifest = new CapabilityManifest(CapabilityManifestSource.CUSTOM);
        manifest.setCapabilities(List.of(power));
        device.setCapabilityManifest(manifest);

        DeviceDiagnosticsResponse result = service(device, null, List.of())
                .diagnose("device-1", "owner@example.com");

        assertFalse(result.checks().stream().anyMatch(check -> check.key().equals("telemetry")));
        assertFalse(result.checks().stream().anyMatch(check -> check.key().equals("metrics")));
        assertTrue(result.checks().stream().anyMatch(check -> check.key().equals("control")));
        assertEquals(DeviceHealthStatus.HEALTHY, result.overallStatus());
    }

    @Test
    void foreignDeviceIsRejected() {
        DeviceDiagnosticsService service = service(device("owner@example.com"), null, List.of());

        assertThrows(RuntimeException.class, () -> service.diagnose("device-1", "other@example.com"));
    }

    @Test
    void offlineDeviceReportsFailedConnectivity() {
        Device device = temperatureDevice();
        device.setLastSeenAt(Instant.now().minusSeconds(60));

        DiagnosticCheck check = findCheck(service(device, null, List.of())
                .diagnose("device-1", "owner@example.com"), "connectivity");

        assertEquals(DiagnosticCheckStatus.FAIL, check.status());
    }

    @Test
    void staleTelemetryProducesFailedFreshnessCheck() {
        Device device = temperatureDevice();
        SensorReading reading = reading(Instant.now().minusSeconds(60), true);

        DiagnosticCheck check = findCheck(service(device, reading, List.of())
                .diagnose("device-1", "owner@example.com"), "telemetry");

        assertEquals(DiagnosticCheckStatus.FAIL, check.status());
    }

    @Test
    void sensorWithoutTelemetryReturnsUnknownTelemetryChecks() {
        DeviceDiagnosticsResponse result = service(temperatureDevice(), null, List.of())
                .diagnose("device-1", "owner@example.com");

        assertEquals(DiagnosticCheckStatus.UNKNOWN, findCheck(result, "telemetry").status());
        assertEquals(DiagnosticCheckStatus.UNKNOWN, findCheck(result, "metrics").status());
        assertEquals(DeviceHealthStatus.UNKNOWN, result.overallStatus());
    }

    @Test
    void unresolvedWarningAlertProducesWarning() {
        Alert alert = alert("warning-1", AlertSeverity.WARNING, AlertStatus.ACTIVE, Instant.now());

        DeviceDiagnosticsResponse result = service(temperatureDevice(), null, List.of(alert))
                .diagnose("device-1", "owner@example.com");

        assertEquals(DiagnosticCheckStatus.WARNING, findCheck(result, "alerts").status());
        assertEquals(DeviceHealthStatus.WARNING, result.overallStatus());
    }

    @Test
    void unknownCheckWithWarningProducesWarning() {
        Alert alert = alert("warning-unknown-1", AlertSeverity.WARNING, AlertStatus.ACTIVE, Instant.now());

        DeviceDiagnosticsResponse result = service(temperatureDevice(), null, List.of(alert))
                .diagnose("device-1", "owner@example.com");

        assertEquals(DiagnosticCheckStatus.UNKNOWN, findCheck(result, "telemetry").status());
        assertEquals(DeviceHealthStatus.WARNING, result.overallStatus());
    }

    @Test
    void unknownCheckWithFailureProducesCriticalResult() {
        Device device = temperatureDevice();
        device.setLastSeenAt(Instant.now().minusSeconds(60));

        DeviceDiagnosticsResponse result = service(device, null, List.of())
                .diagnose("device-1", "owner@example.com");

        assertEquals(DiagnosticCheckStatus.FAIL, findCheck(result, "connectivity").status());
        assertEquals(DiagnosticCheckStatus.UNKNOWN, findCheck(result, "telemetry").status());
        assertEquals(DeviceHealthStatus.CRITICAL, result.overallStatus());
    }

    @Test
    void unresolvedCriticalAlertProducesCriticalResult() {
        Alert alert = alert("critical-1", AlertSeverity.CRITICAL, AlertStatus.ACKNOWLEDGED, Instant.now());

        DeviceDiagnosticsResponse result = service(temperatureDevice(), null, List.of(alert))
                .diagnose("device-1", "owner@example.com");

        assertEquals(DiagnosticCheckStatus.FAIL, findCheck(result, "alerts").status());
        assertEquals(DeviceHealthStatus.CRITICAL, result.overallStatus());
    }

    @Test
    void resolvedAlertDoesNotAffectCurrentDiagnostics() {
        Alert alert = alert("resolved-1", AlertSeverity.CRITICAL, AlertStatus.RESOLVED, Instant.now());

        DeviceDiagnosticsResponse result = service(
                temperatureDevice(),
                reading(Instant.now(), true),
                List.of(alert))
                .diagnose("device-1", "owner@example.com");

        assertEquals(DiagnosticCheckStatus.PASS, findCheck(result, "alerts").status());
        assertEquals(DeviceHealthStatus.HEALTHY, result.overallStatus());
    }

    @Test
    void missingConfiguredMetricProducesWarning() {
        Device device = temperatureDevice();
        SensorReading reading = reading(Instant.now(), false);

        DiagnosticCheck check = findCheck(service(device, reading, List.of())
                .diagnose("device-1", "owner@example.com"), "metrics");

        assertEquals(DiagnosticCheckStatus.WARNING, check.status());
        assertTrue(check.message().contains("Temperature"));
    }

    @Test
    void diagnosticsDoesNotMutateDeviceOrAlertState() {
        Device device = temperatureDevice();
        Instant originalLastSeen = device.getLastSeenAt();
        Alert alert = alert("alert-1", AlertSeverity.WARNING, AlertStatus.ACTIVE, Instant.now());
        AlertStatus originalStatus = alert.getStatus();

        service(device, reading(Instant.now(), true), List.of(alert))
                .diagnose("device-1", "owner@example.com");

        assertEquals(originalLastSeen, device.getLastSeenAt());
        assertEquals(originalStatus, alert.getStatus());
        assertNull(alert.getAcknowledgedAt());
        assertNull(alert.getResolvedAt());
    }

    @Test
    void activityIncludesDeviceCreatedAndPersistedAlertLifecycleTimestamps() {
        Device device = temperatureDevice();
        Instant created = Instant.parse("2026-10-12T10:00:00Z");
        device.setCreatedAt(created);
        Alert alert = alert("alert-1", AlertSeverity.WARNING, AlertStatus.RESOLVED,
                Instant.parse("2026-10-12T10:03:00Z"));
        Instant acknowledged = Instant.parse("2026-10-12T10:04:00Z");
        Instant resolved = Instant.parse("2026-10-12T10:05:00Z");
        alert.setAcknowledgedAt(acknowledged);
        alert.setResolvedAt(resolved);

        List<DeviceActivityResponse> events = service(device, null, List.of(alert))
                .activity("device-1", "owner@example.com");

        assertEquals(created, event(events, "DEVICE_CREATED").timestamp());
        assertEquals(alert.getCreatedAt(), event(events, "ALERT_CREATED").timestamp());
        assertEquals(acknowledged, event(events, "ALERT_ACKNOWLEDGED").timestamp());
        assertEquals(resolved, event(events, "ALERT_RESOLVED").timestamp());
    }

    @Test
    void activityContainsOnlySelectedDeviceEvents() {
        Device device = temperatureDevice();
        Alert selected = alert("selected", AlertSeverity.INFO, AlertStatus.ACTIVE, Instant.now());
        selected.setDeviceId("device-1");
        Alert foreign = alert("foreign", AlertSeverity.INFO, AlertStatus.ACTIVE, Instant.now());
        foreign.setDeviceId("device-2");

        List<DeviceActivityResponse> events = service(device, null, List.of(selected, foreign))
                .activity("device-1", "owner@example.com");

        assertTrue(events.stream().allMatch(item -> !"foreign".equals(item.sourceId())));
    }

    @Test
    void foreignDeviceActivityIsRejected() {
        assertThrows(RuntimeException.class, () -> service(device("owner@example.com"), null, List.of())
                .activity("device-1", "other@example.com"));
    }

    @Test
    void activityIsNewestFirstAndLimitedToTwentyEvents() {
        Device device = temperatureDevice();
        device.setCreatedAt(Instant.parse("2026-10-01T00:00:00Z"));
        List<Alert> alerts = new java.util.ArrayList<>();
        for (int index = 0; index < 25; index++) {
            Alert alert = alert("alert-" + index, AlertSeverity.INFO, AlertStatus.ACTIVE,
                    Instant.parse("2026-10-01T00:00:00Z").plusSeconds(index * 60L));
            alert.setDeviceId("device-1");
            alerts.add(alert);
        }

        List<DeviceActivityResponse> events = service(device, null, alerts)
                .activity("device-1", "owner@example.com");

        assertEquals(20, events.size());
        assertTrue(events.get(0).timestamp().compareTo(events.get(events.size() - 1).timestamp()) >= 0);
    }

    @Test
    void activityDoesNotFabricateLifecycleEventsWithoutTimestamps() {
        Device device = temperatureDevice();
        Alert alert = alert("untimed", AlertSeverity.WARNING, AlertStatus.RESOLVED, null);
        alert.setAcknowledgedAt(null);
        alert.setResolvedAt(null);

        assertTrue(service(device, null, List.of(alert))
                .activity("device-1", "owner@example.com").isEmpty());
    }

    private DeviceDiagnosticsService service(Device device, SensorReading reading, List<com.nexaiot.server.alert.Alert> alerts) {
        DeviceRepository devices = (DeviceRepository) Proxy.newProxyInstance(
                DeviceRepository.class.getClassLoader(), new Class[]{DeviceRepository.class},
                (proxy, method, args) -> method.getName().equals("findByIdAndOwnerEmail")
                        && device.getOwnerEmail().equals(args[1]) ? Optional.of(device) : Optional.empty()
        );
        SensorReadingRepository readings = (SensorReadingRepository) Proxy.newProxyInstance(
                SensorReadingRepository.class.getClassLoader(), new Class[]{SensorReadingRepository.class},
                (proxy, method, args) -> method.getName().equals("findTopByDeviceIdOrderByReceivedAtDesc")
                        ? Optional.ofNullable(reading) : List.of()
        );
        AlertRepository alertRepository = (AlertRepository) Proxy.newProxyInstance(
                AlertRepository.class.getClassLoader(), new Class[]{AlertRepository.class},
                (proxy, method, args) -> method.getName().equals("findByDeviceIdAndOwnerEmailOrderByCreatedAtDesc")
                        ? alerts.stream().filter(alert -> alert.getDeviceId() == null || alert.getDeviceId().equals(args[0])).toList()
                        : List.of()
        );
        return new DeviceDiagnosticsService(devices, readings, alertRepository);
    }

    private Device device(String owner) {
        Device device = new Device();
        device.setId("device-1");
        device.setOwnerEmail(owner);
        device.setName("Sensor");
        device.setStatus("ONLINE");
        device.setLastSeenAt(Instant.now());
        return device;
    }

    private Device temperatureDevice() {
        Device device = device("owner@example.com");
        CapabilityDefinition temperature = new CapabilityDefinition();
        temperature.setKey("temperature");
        temperature.setName("Temperature");
        temperature.setCategory(CapabilityCategory.MEASUREMENT);
        temperature.setDataType(CapabilityDataType.NUMBER);
        CapabilityManifest manifest = new CapabilityManifest(CapabilityManifestSource.CUSTOM);
        manifest.setCapabilities(List.of(temperature));
        device.setCapabilityManifest(manifest);
        return device;
    }

    private SensorReading reading(Instant receivedAt, boolean includeTemperature) {
        SensorReading reading = new SensorReading();
        reading.setId("reading-1");
        reading.setReceivedAt(receivedAt);
        reading.setMetrics(includeTemperature ? Map.of("temperature", 28.0) : Map.of());
        return reading;
    }

    private Alert alert(String id, AlertSeverity severity, AlertStatus status, Instant createdAt) {
        Alert alert = new Alert();
        alert.setId(id);
        alert.setDeviceId("device-1");
        alert.setSeverity(severity);
        alert.setStatus(status);
        alert.setCreatedAt(createdAt);
        return alert;
    }

    private DiagnosticCheck findCheck(DeviceDiagnosticsResponse response, String key) {
        return response.checks().stream()
                .filter(check -> check.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private DeviceActivityResponse event(List<DeviceActivityResponse> events, String type) {
        return events.stream()
                .filter(item -> item.type().equals(type))
                .findFirst()
                .orElseThrow();
    }
}
