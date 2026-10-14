package com.nexaiot.server.report;

public class TelemetryMetricSummary {
    private String label;
    private String unit;
    private long count;
    private Double minimum;
    private Double maximum;
    private Double average;
    private Double latest;
    public TelemetryMetricSummary() { }
    public TelemetryMetricSummary(String label, String unit, long count, Double minimum, Double maximum, Double average, Double latest) { this.label = label; this.unit = unit; this.count = count; this.minimum = minimum; this.maximum = maximum; this.average = average; this.latest = latest; }
    public String getLabel() { return label; }
    public String getUnit() { return unit; }
    public long getCount() { return count; }
    public Double getMinimum() { return minimum; }
    public Double getMaximum() { return maximum; }
    public Double getAverage() { return average; }
    public Double getLatest() { return latest; }
}
