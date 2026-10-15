package com.nexaiot.server.mqtt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MqttTopicSecurityTest {

    @Test
    void acceptsExactTelemetryTopic() {
        assertEquals(
                "NEXA-123",
                MqttTopicSecurity.parseInboundDeviceKey(
                        "nexaiot/devices/NEXA-123/telemetry", "telemetry")
        );
    }

    @Test
    void rejectsWrongPrefixAndExtraSegments() {
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopicSecurity.parseInboundDeviceKey(
                        "other/devices/NEXA-123/telemetry", "telemetry"));
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopicSecurity.parseInboundDeviceKey(
                        "nexaiot/devices/NEXA-123/telemetry/extra", "telemetry"));
    }

    @Test
    void rejectsEmptyAndWildcardDeviceKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopicSecurity.parseInboundDeviceKey(
                        "nexaiot/devices//telemetry", "telemetry"));
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopicSecurity.parseInboundDeviceKey(
                        "nexaiot/devices/NEXA+/telemetry", "telemetry"));
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopicSecurity.parseInboundDeviceKey(
                        "nexaiot/devices/NEXA#/telemetry", "telemetry"));
    }

    @Test
    void rejectsUnexpectedTopicTypeAndMalformedSeparators() {
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopicSecurity.parseInboundDeviceKey(
                        "nexaiot/devices/NEXA-123/commands", "telemetry"));
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopicSecurity.parseInboundDeviceKey(
                        "nexaiot//NEXA-123/telemetry", "telemetry"));
    }

    @Test
    void constructsCommandTopicOnlyFromSafeDeviceKey() {
        assertEquals(
                "nexaiot/devices/NEXA-123/commands",
                MqttTopicSecurity.commandTopic("NEXA-123")
        );
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopicSecurity.commandTopic("NEXA-123/other"));
    }
}
