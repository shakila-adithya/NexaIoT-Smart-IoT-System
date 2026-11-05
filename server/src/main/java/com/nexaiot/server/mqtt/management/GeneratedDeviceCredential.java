package com.nexaiot.server.mqtt.management;

/**
 * Sensitive in-memory handoff only. The password must never be logged or
 * persisted; B4 will define the authenticated encrypted delivery channel.
 */
public record GeneratedDeviceCredential(
        String username,
        String clientId,
        String password
) {
    @Override
    public String toString() {
        return "GeneratedDeviceCredential[username=" + username
                + ", clientId=" + clientId + ", password=<redacted>]";
    }
}
