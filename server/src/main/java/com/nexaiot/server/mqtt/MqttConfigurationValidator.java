package com.nexaiot.server.mqtt;

import java.net.URI;

/** Validates the security-sensitive parts of the MQTT client configuration. */
public final class MqttConfigurationValidator {

    private MqttConfigurationValidator() {
    }

    public static void validate(
            String brokerUrl,
            String username,
            String password,
            boolean authRequired,
            boolean tlsRequired
    ) {
        if (brokerUrl == null || brokerUrl.isBlank()) {
            throw new IllegalArgumentException("MQTT broker URL is required.");
        }

        URI brokerUri;
        try {
            brokerUri = URI.create(brokerUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("MQTT broker URL is invalid.");
        }

        if (brokerUri.getScheme() == null || brokerUri.getHost() == null) {
            throw new IllegalArgumentException("MQTT broker URL is invalid.");
        }

        boolean hasUsername = username != null && !username.isBlank();
        boolean hasPassword = password != null && !password.isBlank();

        if (authRequired && (!hasUsername || !hasPassword)) {
            throw new IllegalArgumentException("MQTT authentication requires credentials.");
        }

        if (hasUsername != hasPassword) {
            throw new IllegalArgumentException("MQTT username and password must be configured together.");
        }

        if (tlsRequired && !usesTls(brokerUrl)) {
            throw new IllegalArgumentException("MQTT TLS is required for this deployment.");
        }
    }

    public static boolean usesTls(String brokerUrl) {
        if (brokerUrl == null || brokerUrl.isBlank()) {
            return false;
        }

        try {
            String scheme = URI.create(brokerUrl.trim()).getScheme();
            return "ssl".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
