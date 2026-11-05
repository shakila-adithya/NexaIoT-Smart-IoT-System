package com.nexaiot.server.device;

import java.time.Instant;

/**
 * Physical enrollment lifecycle, intentionally separate from runtime status.
 */
public class DeviceProvisioningInfo {

    private DeviceProvisioningStatus status;
    private Instant provisionedAt;
    private Instant lastProvisioningAttemptAt;
    private String failureReason;

    public DeviceProvisioningStatus getStatus() {
        return status;
    }

    public void setStatus(DeviceProvisioningStatus status) {
        this.status = status;
    }

    public Instant getProvisionedAt() {
        return provisionedAt;
    }

    public void setProvisionedAt(Instant provisionedAt) {
        this.provisionedAt = provisionedAt;
    }

    public Instant getLastProvisioningAttemptAt() {
        return lastProvisioningAttemptAt;
    }

    public void setLastProvisioningAttemptAt(Instant lastProvisioningAttemptAt) {
        this.lastProvisioningAttemptAt = lastProvisioningAttemptAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
