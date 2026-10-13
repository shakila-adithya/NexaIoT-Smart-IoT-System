package com.nexaiot.server.device.diagnostics;

import java.time.Instant;

public record DiagnosticCheck(
        String key,
        String label,
        DiagnosticCheckStatus status,
        String message,
        Instant observedAt
) {
}
