package com.nexaiot.server.report;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class TelemetryTrendPoint {
    private Instant bucketStart;
    private Map<String, Double> metrics = new LinkedHashMap<>();
    public TelemetryTrendPoint() { }
    public TelemetryTrendPoint(Instant bucketStart, Map<String, Double> metrics) { this.bucketStart = bucketStart; this.metrics = metrics; }
    public Instant getBucketStart() { return bucketStart; }
    public void setBucketStart(Instant value) { bucketStart = value; }
    public Map<String, Double> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Double> value) { metrics = value; }
}
