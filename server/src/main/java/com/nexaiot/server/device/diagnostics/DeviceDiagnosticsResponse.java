package com.nexaiot.server.device.diagnostics;

import java.time.Instant;
import java.util.List;

public record DeviceDiagnosticsResponse(
        String deviceId,
        String deviceName,
        Instant checkedAt,
        DeviceHealthStatus overallStatus,
        List<DiagnosticCheck> checks
) {
}
