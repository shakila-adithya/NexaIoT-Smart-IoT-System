package com.nexaiot.server.mqtt;

import java.time.Instant;

/** One atomic view of a retained command and its acknowledgement outcome. */
public record MqttCommandStatusSnapshot(
        MqttPendingCommand command, MqttCommandResultStatus result, Instant observedAt
) {
    public String status() {
        return result == null ? "PENDING" : result.status();
    }
}
