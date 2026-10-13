package com.nexaiot.server.device.diagnostics;

import java.time.Instant;

public record DeviceActivityResponse(
        String id,
        String type,
        String title,
        String description,
        Instant timestamp,
        String sourceId
) {
}
