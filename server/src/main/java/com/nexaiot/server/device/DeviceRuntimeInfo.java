package com.nexaiot.server.device;

import java.time.Instant;

/**
 * Optional runtime observations. Existing lastSeenAt/status behavior remains
 * authoritative until a later freshness-policy migration.
 */
public class DeviceRuntimeInfo {

    private DeviceConnectivityStatus connectivityStatus;
    private Instant lastTelemetryAt;
    private Instant lastStateAt;
    private Instant lastHeartbeatAt;

    public DeviceConnectivityStatus getConnectivityStatus() {
        return connectivityStatus;
    }

    public void setConnectivityStatus(DeviceConnectivityStatus connectivityStatus) {
        this.connectivityStatus = connectivityStatus;
    }

    public Instant getLastTelemetryAt() {
        return lastTelemetryAt;
    }

    public void setLastTelemetryAt(Instant lastTelemetryAt) {
        this.lastTelemetryAt = lastTelemetryAt;
    }

    public Instant getLastStateAt() {
        return lastStateAt;
    }

    public void setLastStateAt(Instant lastStateAt) {
        this.lastStateAt = lastStateAt;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }
}
