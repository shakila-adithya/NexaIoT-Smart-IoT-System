package com.nexaiot.server.device.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record ControlDeviceRequest(
        Boolean power,
        String capabilityKey,
        JsonNode value
) {
    public ControlDeviceRequest(Boolean power) {
        this(power, null, null);
    }
}
