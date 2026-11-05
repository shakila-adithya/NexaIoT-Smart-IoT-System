package com.nexaiot.server.mqtt.management;

public record BrokerDeviceIdentity(
        String username,
        String clientId
) {
}
