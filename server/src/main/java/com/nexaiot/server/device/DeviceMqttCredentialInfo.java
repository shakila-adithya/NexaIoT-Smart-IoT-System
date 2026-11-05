package com.nexaiot.server.device;

import java.time.Instant;

/** Metadata only. No MQTT password is stored here. */
public class DeviceMqttCredentialInfo {

    private String credentialId;
    private String username;
    private MqttCredentialStatus status;
    private Instant issuedAt;
    private Instant revokedAt;
    private String lastEnrollmentError;

    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public MqttCredentialStatus getStatus() { return status; }
    public void setStatus(MqttCredentialStatus status) { this.status = status; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getLastEnrollmentError() { return lastEnrollmentError; }
    public void setLastEnrollmentError(String lastEnrollmentError) { this.lastEnrollmentError = lastEnrollmentError; }
}
