package com.nexaiot.server.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.jupiter.api.Test;
import org.springframework.integration.mqtt.core.Mqttv3ClientManager;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.inbound.AbstractMqttMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageHandler;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void commandResultSubscriptionUsesDeviceWildcardTopic() {
        MqttConfig config = new MqttConfig(
                "ssl://broker.example:8883",
                "nexaiot-test",
                "nexaiot/devices/+/telemetry",
                "nexaiot/devices/+/state",
                1,
                "service-user",
                "service-password",
                true,
                true
        );

        assertEquals("nexaiot/devices/+/command-results", config.commandResultTopic());
    }

    @Test
    void allMqttAdaptersShareConfiguredClientManagerAndClientId() throws Exception {
        MqttConfig config = new MqttConfig(
                "ssl://broker.example:8883",
                "nexaiot-test",
                "nexaiot/devices/+/telemetry",
                "nexaiot/devices/+/state",
                1,
                "service-user",
                "service-password",
                true,
                true
        );

        Mqttv3ClientManager manager = config.mqttClientManager();
        MessageProducer telemetry = config.mqttInbound(manager);
        MessageProducer state = config.mqttStateInbound(manager);
        MessageProducer commandResults = config.mqttCommandResultInbound(manager);
        MessageHandler outbound = config.mqttOutbound(manager);

        assertSame(manager, inboundManager(telemetry));
        assertSame(manager, inboundManager(state));
        assertSame(manager, inboundManager(commandResults));
        assertSame(manager, outboundManager(outbound));
        assertEquals("nexaiot-test", manager.getClientId());
        assertEquals("nexaiot-test", ((MqttPahoMessageHandler) outbound).getClientId());
    }

    @Test
    void sharedManagerPreservesConfiguredConnectionOptions() {
        MqttConfig config = new MqttConfig(
                "ssl://broker.example:8883",
                "nexaiot-test",
                "nexaiot/devices/+/telemetry",
                "nexaiot/devices/+/state",
                1,
                "service-user",
                "service-password",
                true,
                true
        );

        MqttConnectOptions options = config.mqttClientManager().getConnectionInfo();

        assertArrayEquals(new String[]{"ssl://broker.example:8883"}, options.getServerURIs());
        assertEquals("service-user", options.getUserName());
        assertArrayEquals("service-password".toCharArray(), options.getPassword());
        assertTrue(options.isAutomaticReconnect());
        assertTrue(options.isCleanSession());
    }

    private static Mqttv3ClientManager inboundManager(MessageProducer producer) throws Exception {
        Method method = AbstractMqttMessageDrivenChannelAdapter.class
                .getDeclaredMethod("getClientManager");
        method.setAccessible(true);
        return (Mqttv3ClientManager) method.invoke(producer);
    }

    private static Mqttv3ClientManager outboundManager(MessageHandler handler) throws Exception {
        Method method = MqttPahoMessageHandler.class.getSuperclass()
                .getDeclaredMethod("getClientManager");
        method.setAccessible(true);
        return (Mqttv3ClientManager) method.invoke(handler);
    }
}
