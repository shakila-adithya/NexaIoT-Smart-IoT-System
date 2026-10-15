package com.nexaiot.server.mqtt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MqttConfigurationValidatorTest {

    @Test
    void anonymousLocalConfigurationIsAllowed() {
        assertDoesNotThrow(() -> MqttConfigurationValidator.validate(
                "tcp://localhost:1883", "", "", false, false));
    }

    @Test
    void requiredAuthenticationRejectsMissingOrPartialCredentials() {
        assertThrows(IllegalArgumentException.class,
                () -> MqttConfigurationValidator.validate(
                        "tcp://localhost:1883", "", "", true, false));
        assertThrows(IllegalArgumentException.class,
                () -> MqttConfigurationValidator.validate(
                        "tcp://localhost:1883", "user", "", false, false));
    }

    @Test
    void configuredCredentialsAreAccepted() {
        assertDoesNotThrow(() -> MqttConfigurationValidator.validate(
                "tcp://broker.example:1883", "user", "password", true, false));
    }

    @Test
    void requiredTlsRejectsPlaintextAndAcceptsSsl() {
        assertThrows(IllegalArgumentException.class,
                () -> MqttConfigurationValidator.validate(
                        "tcp://broker.example:1883", "", "", false, true));
        assertDoesNotThrow(() -> MqttConfigurationValidator.validate(
                "ssl://broker.example:8883", "", "", false, true));
    }

    @Test
    void malformedBrokerUriIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MqttConfigurationValidator.validate(
                        "not-a-broker", "", "", false, false));
    }
}
