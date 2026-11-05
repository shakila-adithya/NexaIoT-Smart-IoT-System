package com.nexaiot.server.enrollment;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/** One-time, short-lived handoff authorization. It contains no password. */
public final class CredentialDeliveryGrant {

    private final String deviceKey;
    private final String enrollmentOperationId;
    private final String challenge;
    private final Instant expiresAt;
    private final AtomicBoolean consumed = new AtomicBoolean();

    CredentialDeliveryGrant(String deviceKey, String enrollmentOperationId,
            String challenge, Instant expiresAt) {
        this.deviceKey = deviceKey;
        this.enrollmentOperationId = enrollmentOperationId;
        this.challenge = challenge;
        this.expiresAt = expiresAt;
    }

    public String deviceKey() { return deviceKey; }
    public String enrollmentOperationId() { return enrollmentOperationId; }
    public String challenge() { return challenge; }
    public Instant expiresAt() { return expiresAt; }

    boolean consume(Instant now) {
        return now != null && now.isBefore(expiresAt) && consumed.compareAndSet(false, true);
    }
}
