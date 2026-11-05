package com.nexaiot.server.simulator;

import java.net.URI;
import java.nio.file.Path;

/** Validated TLS configuration data; it does not open a broker connection. */
public record SimulatorMqttConfiguration(
        URI brokerUri,
        Path caCertificate,
        String clientId
) {

    public SimulatorMqttConfiguration {
        if (brokerUri == null || !"ssl".equalsIgnoreCase(brokerUri.getScheme())
                || brokerUri.getHost() == null || brokerUri.getPort() <= 0) {
            throw new IllegalArgumentException("Simulator MQTT broker must use ssl://host:port.");
        }
        if (caCertificate == null || caCertificate.toString().isBlank()) {
            throw new IllegalArgumentException("Simulator MQTT CA certificate is required.");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("Simulator MQTT client id is required.");
        }
    }
}
