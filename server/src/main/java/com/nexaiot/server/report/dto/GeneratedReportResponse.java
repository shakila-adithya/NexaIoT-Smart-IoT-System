package com.nexaiot.server.report.dto;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import com.nexaiot.server.report.TelemetryMetricSummary;
import com.nexaiot.server.report.TelemetryTrendPoint;
public record GeneratedReportResponse(String id, String deviceId, String deviceName, String range, Instant periodStart, Instant periodEnd, Instant generatedAt, String deviceStatus, Instant lastSeenAt, Map<String, TelemetryMetricSummary> telemetrySummary, List<TelemetryTrendPoint> telemetryTrend, Map<String, Integer> severityCounts, Map<String, Integer> statusCounts, int alertCount) { }
