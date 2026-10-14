package com.nexaiot.server.report;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.nexaiot.server.alert.Alert;
import com.nexaiot.server.alert.AlertRepository;
import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.device.CapabilityManifestAdapter;
import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceNotFoundException;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.sensor.SensorReading;
import com.nexaiot.server.sensor.SensorReadingRepository;
import com.nexaiot.server.report.dto.GenerateReportRequest;
import com.nexaiot.server.report.dto.GeneratedReportResponse;

@Service
public class GeneratedReportService {
    private static final List<String> RANGES = List.of("1h", "6h", "24h", "7d");
    private final GeneratedReportRepository reportRepository;
    private final DeviceRepository deviceRepository;
    private final SensorReadingRepository readingRepository;
    private final AlertRepository alertRepository;

    public GeneratedReportService(GeneratedReportRepository reportRepository, DeviceRepository deviceRepository, SensorReadingRepository readingRepository, AlertRepository alertRepository) {
        this.reportRepository = reportRepository; this.deviceRepository = deviceRepository; this.readingRepository = readingRepository; this.alertRepository = alertRepository;
    }
    public List<GeneratedReportResponse> list(String owner) { return reportRepository.findByOwnerEmailOrderByGeneratedAtDesc(normalize(owner)).stream().map(this::response).toList(); }
    public GeneratedReportResponse get(String id, String owner) { return response(findOwned(id, owner)); }
    public GeneratedReportResponse generate(GenerateReportRequest request, String ownerEmail) {
        String owner = normalize(ownerEmail);
        if (request.getDeviceId() == null || request.getDeviceId().isBlank()) throw new IllegalArgumentException("A device is required to generate a report.");
        if (request.getRange() == null || !RANGES.contains(request.getRange())) throw new IllegalArgumentException("Unsupported report range. Use 1h, 6h, 24h, or 7d.");
        Device device = deviceRepository.findByIdAndOwnerEmail(request.getDeviceId(), owner).orElseThrow(() -> new DeviceNotFoundException("Device not found"));
        Instant end = Instant.now(); Instant start = end.minus(duration(request.getRange()));
        List<SensorReading> readings = readingRepository.findByDeviceIdAndReceivedAtBetweenOrderByReceivedAtAsc(device.getId(), start, end);
        List<Alert> alerts = alertRepository.findByDeviceIdAndOwnerEmailAndCreatedAtBetweenOrderByCreatedAtAsc(device.getId(), owner, start, end);
        GeneratedReport report = new GeneratedReport(); report.setOwnerEmail(owner); report.setDeviceId(device.getId()); report.setDeviceName(device.getName()); report.setRange(request.getRange()); report.setPeriodStart(start); report.setPeriodEnd(end); report.setGeneratedAt(end); report.setDeviceStatus(effectiveStatus(device, end)); report.setLastSeenAt(device.getLastSeenAt());
        report.setTelemetrySummary(aggregate(device, readings));
        report.setTelemetryTrend(aggregateTrend(device, readings, start, end, request.getRange()));
        Map<String, Integer> severities = new LinkedHashMap<>(), statuses = new LinkedHashMap<>();
        for (Alert alert : alerts) { if (alert.getSeverity() != null) severities.merge(alert.getSeverity().name(), 1, Integer::sum); if (alert.getStatus() != null) statuses.merge(alert.getStatus().name(), 1, Integer::sum); }
        report.setSeverityCounts(severities); report.setStatusCounts(statuses); report.setAlertCount(alerts.size());
        return response(reportRepository.save(report));
    }
    public void delete(String id, String owner) { reportRepository.delete(findOwned(id, owner)); }

    private Map<String, TelemetryMetricSummary> aggregate(Device device, List<SensorReading> readings) {
        Map<String, TelemetryMetricSummary> result = new LinkedHashMap<>();
        for (CapabilityDefinition capability : numericCapabilities(device)) {
            List<Double> values = new ArrayList<>();
            for (SensorReading reading : readings) { Double value = valueFor(capability.getKey(), reading); if (value != null && Double.isFinite(value)) values.add(value); }
            if (values.isEmpty()) continue;
            double sum = values.stream().mapToDouble(Double::doubleValue).sum();
            result.put(capability.getKey(), new TelemetryMetricSummary(capability.getName() == null ? capability.getKey() : capability.getName(), capability.getUnit(), values.size(), values.stream().mapToDouble(Double::doubleValue).min().orElseThrow(), values.stream().mapToDouble(Double::doubleValue).max().orElseThrow(), sum / values.size(), values.get(values.size() - 1)));
        }
        return result;
    }
    private List<TelemetryTrendPoint> aggregateTrend(Device device, List<SensorReading> readings, Instant start, Instant end, String range) {
        long bucketSeconds = switch (range) { case "1h" -> 300; case "6h" -> 900; case "24h" -> 3600; case "7d" -> 21600; default -> throw new IllegalArgumentException("Unsupported report range."); };
        Map<Instant, Map<String, List<Double>>> buckets = new java.util.TreeMap<>();
        for (SensorReading reading : readings) {
            if (reading.getReceivedAt() == null || reading.getReceivedAt().isBefore(start) || reading.getReceivedAt().isAfter(end)) continue;
            long elapsed = Duration.between(start, reading.getReceivedAt()).getSeconds();
            Instant bucketStart = start.plusSeconds((elapsed / bucketSeconds) * bucketSeconds);
            Map<String, List<Double>> bucket = buckets.computeIfAbsent(bucketStart, ignored -> new LinkedHashMap<>());
            for (CapabilityDefinition capability : numericCapabilities(device)) {
                Double value = valueFor(capability.getKey(), reading);
                if (value != null && Double.isFinite(value)) bucket.computeIfAbsent(capability.getKey(), ignored -> new ArrayList<>()).add(value);
            }
        }
        List<TelemetryTrendPoint> result = new ArrayList<>();
        buckets.forEach((bucketStart, values) -> {
            Map<String, Double> averages = new LinkedHashMap<>();
            values.forEach((key, samples) -> averages.put(key, samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow()));
            if (!averages.isEmpty()) result.add(new TelemetryTrendPoint(bucketStart, averages));
        });
        return result;
    }
    private List<CapabilityDefinition> numericCapabilities(Device device) {
        CapabilityManifest manifest = CapabilityManifestAdapter.effectiveManifest(device);
        if (manifest.getCapabilities() == null) return List.of();
        return manifest.getCapabilities().stream().filter(capability -> capability.getCategory() != CapabilityCategory.CONTROL && capability.getDataType() == CapabilityDataType.NUMBER).toList();
    }
    private Double valueFor(String key, SensorReading reading) { if ("battery".equals(key)) return reading.getBattery() == null ? null : reading.getBattery().doubleValue(); if ("rssi".equals(key)) return reading.getRssi() == null ? null : reading.getRssi().doubleValue(); return reading.getMetrics() == null ? null : reading.getMetrics().get(key); }
    private Duration duration(String range) { return switch (range) { case "1h" -> Duration.ofHours(1); case "6h" -> Duration.ofHours(6); case "24h" -> Duration.ofHours(24); case "7d" -> Duration.ofDays(7); default -> throw new IllegalArgumentException("Unsupported report range."); }; }
    private String effectiveStatus(Device device, Instant now) { if ("MAINTENANCE".equalsIgnoreCase(device.getStatus())) return "MAINTENANCE"; Instant lastSeenAt = device.getLastSeenAt(); return lastSeenAt != null && lastSeenAt.isAfter(now.minusSeconds(5)) ? "ONLINE" : "OFFLINE"; }
    private GeneratedReport findOwned(String id, String owner) { return reportRepository.findByIdAndOwnerEmail(id, normalize(owner)).orElseThrow(() -> new GeneratedReportNotFoundException("Report not found")); }
    private GeneratedReportResponse response(GeneratedReport report) { return new GeneratedReportResponse(report.getId(), report.getDeviceId(), report.getDeviceName(), report.getRange(), report.getPeriodStart(), report.getPeriodEnd(), report.getGeneratedAt(), report.getDeviceStatus(), report.getLastSeenAt(), report.getTelemetrySummary(), report.getTelemetryTrend() == null ? List.of() : report.getTelemetryTrend(), report.getSeverityCounts(), report.getStatusCounts(), report.getAlertCount()); }
    private String normalize(String value) { return value.trim().toLowerCase(); }
}
