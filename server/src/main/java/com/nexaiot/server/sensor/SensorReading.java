package com.nexaiot.server.sensor;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "sensor_readings")
@CompoundIndex(
    name = "device_received_at_idx",
    def = "{'deviceId': 1, 'receivedAt': -1}"
)
public class SensorReading {

    @Id
    private String id;

    private String deviceId;
    private String deviceKey;
    private Map<String, Double> metrics;
    private Integer battery;
    private Integer rssi;
    private Instant deviceTimestamp;
    private Instant receivedAt;

    public SensorReading() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceKey() {
        return deviceKey;
    }

    public void setDeviceKey(String deviceKey) {
        this.deviceKey = deviceKey;
    }

    public Map<String, Double> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Double> metrics) {
        this.metrics = metrics;
    }

    public Integer getBattery() {
        return battery;
    }

    public void setBattery(Integer battery) {
        this.battery = battery;
    }

    public Integer getRssi() {
        return rssi;
    }

    public void setRssi(Integer rssi) {
        this.rssi = rssi;
    }

    public Instant getDeviceTimestamp() {
        return deviceTimestamp;
    }

    public void setDeviceTimestamp(Instant deviceTimestamp) {
        this.deviceTimestamp = deviceTimestamp;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}