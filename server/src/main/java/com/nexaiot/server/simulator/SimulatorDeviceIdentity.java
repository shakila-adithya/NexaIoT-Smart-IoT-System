package com.nexaiot.server.simulator;

import com.nexaiot.server.mqtt.MqttTopicSecurity;

/** Stable, non-secret identity presented by the reference simulator. */
public record SimulatorDeviceIdentity(
        String deviceKey,
        String clientId,
        String hardwareIdentityHash
) {

    public SimulatorDeviceIdentity {
        MqttTopicSecurity.validateDeviceKey(deviceKey);
        requireNonBlank(clientId, "Simulator client id is required.");
        requireNonBlank(hardwareIdentityHash, "Simulator hardware identity is required.");
    }

    public static SimulatorDeviceIdentity forDeviceKey(
            String deviceKey,
            String hardwareIdentityHash
    ) {
        MqttTopicSecurity.validateDeviceKey(deviceKey);
        return new SimulatorDeviceIdentity(
                deviceKey,
                "nexaiot-device-" + deviceKey,
                hardwareIdentityHash);
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
