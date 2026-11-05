package com.nexaiot.server.mqtt.management;

public record BrokerOperationResult(
        String operation,
        String target,
        boolean acknowledged
) {
}
