package com.nexaiot.server.device.dto;

import jakarta.validation.constraints.NotNull;

public record ControlDeviceRequest(
        @NotNull Boolean power
) {
}