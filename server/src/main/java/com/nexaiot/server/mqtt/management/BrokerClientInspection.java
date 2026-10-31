package com.nexaiot.server.mqtt.management;

public record BrokerClientInspection(
        String username,
        String clientId,
        boolean clientIdPresent,
        boolean disabled,
        boolean acknowledged
) {
}
