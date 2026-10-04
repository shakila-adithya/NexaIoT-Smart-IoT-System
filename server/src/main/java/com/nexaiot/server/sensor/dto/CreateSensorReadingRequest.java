package com.nexaiot.server.sensor.dto;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

public record CreateSensorReadingRequest(

    @NotEmpty(message = "At least one sensor metric is required")
    Map<String, Double> metrics,

    @Min(value = 0, message = "Battery must be between 0 and 100")
    @Max(value = 100, message = "Battery must be between 0 and 100")
    Integer battery,

    Integer rssi,

    Instant timestamp

) {
}