package com.nexaiot.server.report;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "generated_reports")
public class GeneratedReport {
    @Id private String id;
    private String ownerEmail;
    private String deviceId;
    private String deviceName;
    private String range;
    private Instant periodStart;
    private Instant periodEnd;
    private Instant generatedAt;
    private String deviceStatus;
    private Instant lastSeenAt;
    private Map<String, TelemetryMetricSummary> telemetrySummary = new LinkedHashMap<>();
    private Map<String, Integer> severityCounts = new LinkedHashMap<>();
    private Map<String, Integer> statusCounts = new LinkedHashMap<>();
    private List<TelemetryTrendPoint> telemetryTrend = new ArrayList<>();
    private int alertCount;
    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String value) { ownerEmail = value; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String value) { deviceId = value; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String value) { deviceName = value; }
    public String getRange() { return range; }
    public void setRange(String value) { range = value; }
    public Instant getPeriodStart() { return periodStart; }
    public void setPeriodStart(Instant value) { periodStart = value; }
    public Instant getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Instant value) { periodEnd = value; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant value) { generatedAt = value; }
    public String getDeviceStatus() { return deviceStatus; }
    public void setDeviceStatus(String value) { deviceStatus = value; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant value) { lastSeenAt = value; }
    public Map<String, TelemetryMetricSummary> getTelemetrySummary() { return telemetrySummary; }
    public void setTelemetrySummary(Map<String, TelemetryMetricSummary> value) { telemetrySummary = value; }
    public Map<String, Integer> getSeverityCounts() { return severityCounts; }
    public void setSeverityCounts(Map<String, Integer> value) { severityCounts = value; }
    public Map<String, Integer> getStatusCounts() { return statusCounts; }
    public void setStatusCounts(Map<String, Integer> value) { statusCounts = value; }
    public int getAlertCount() { return alertCount; }
    public void setAlertCount(int value) { alertCount = value; }
    public List<TelemetryTrendPoint> getTelemetryTrend() { return telemetryTrend; }
    public void setTelemetryTrend(List<TelemetryTrendPoint> value) { telemetryTrend = value; }
}
