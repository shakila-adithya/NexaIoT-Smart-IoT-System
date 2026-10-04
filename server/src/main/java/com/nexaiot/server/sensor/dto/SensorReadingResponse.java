package com.nexaiot.server.sensor.dto;

import java.time.Instant;
import java.util.Map;

public record SensorReadingResponse(
    String id,
    String deviceId,
    String deviceKey,
    Map<String, Double> metrics,
    Integer battery,
    Integer rssi,
    Instant deviceTimestamp,
    Instant receivedAt
) {
}