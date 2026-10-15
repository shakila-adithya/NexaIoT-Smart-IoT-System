package com.nexaiot.server.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MqttConfigTest {

    @Test
    void configuredCredentialsAreAppliedToClientOptions() {
        MqttConnectOptions options = new MqttConfig(
                "ssl://broker.example:8883",
                "nexaiot-test",
                "nexaiot/devices/+/telemetry",
                "nexaiot/devices/+/state",
                1,
                "service-user",
                "service-password",
                true,
                true
        ).createConnectOptions();

        assertEquals("service-user", options.getUserName());
        assertArrayEquals("service-password".toCharArray(), options.getPassword());
        assertArrayEquals(
                new String[]{"ssl://broker.example:8883"},
                options.getServerURIs()
        );
    }
}
