package com.nexaiot.server.simulator;

import com.nexaiot.server.mqtt.MqttTopicSecurity;

/** Topic construction shared by simulator fixtures and future device clients. */
public final class SimulatorMqttTopics {

    private SimulatorMqttTopics() {
    }

    public static String telemetry(SimulatorDeviceIdentity identity) {
        return topic(identity, "telemetry");
    }

    public static String state(SimulatorDeviceIdentity identity) {
        return topic(identity, "state");
    }

    public static String commands(SimulatorDeviceIdentity identity) {
        return MqttTopicSecurity.commandTopic(identity.deviceKey());
    }

    public static String commandResults(SimulatorDeviceIdentity identity) {
        if (identity == null) {
            throw new IllegalArgumentException("Simulator identity is required.");
        }
        return "nexaiot/devices/" + identity.deviceKey() + "/command-results";
    }

    private static String topic(SimulatorDeviceIdentity identity, String kind) {
        if (identity == null) {
            throw new IllegalArgumentException("Simulator identity is required.");
        }
        return "nexaiot/devices/" + identity.deviceKey() + "/" + kind;
    }
}
