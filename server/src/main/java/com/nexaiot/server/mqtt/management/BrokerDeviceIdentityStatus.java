package com.nexaiot.server.mqtt.management;

public record BrokerDeviceIdentityStatus(
        String username,
        String clientId,
        boolean disabled,
        boolean acknowledged
) {
}
