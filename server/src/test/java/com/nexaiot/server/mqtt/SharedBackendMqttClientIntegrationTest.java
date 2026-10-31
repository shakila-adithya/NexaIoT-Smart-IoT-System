package com.nexaiot.server.mqtt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.Mqttv3ClientManager;
import org.springframework.integration.mqtt.inbound.AbstractMqttMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageHandler;

@EnabledIfSystemProperty(named = "nexaiot.integration.shared-mqtt-client", matches = "true")
class SharedBackendMqttClientIntegrationTest {

    private static final String TARGET = "ssl://localhost:8884";
    private static final String IDENTITY = "nexaiot-backend-lab";
    private static final String TELEMETRY_TOPIC = "nexaiot/devices/+/telemetry";
    private static final String STATE_TOPIC = "nexaiot/devices/+/state";
    private static final String COMMAND_RESULT_TOPIC = "nexaiot/devices/+/command-results";

    @Test
    void sharedSpringIntegrationClientConnectsAndSubscribesReadOnly() throws Exception {
        String brokerUrl = requiredEnvironment("MQTT_BROKER_URL");
        String caCertificate = requiredEnvironment("MQTT_CA_CERTIFICATE");
        String username = requiredEnvironment("MQTT_USERNAME");
        String password = requiredEnvironment("MQTT_PASSWORD");
        String clientId = requiredEnvironment("MQTT_CLIENT_ID");

        assertEquals(TARGET, brokerUrl, "Broker target mismatch");
        assertEquals(IDENTITY, username, "Backend username mismatch");
        assertEquals(IDENTITY, clientId, "Backend client ID mismatch");
        assertTrue(Files.isRegularFile(Path.of(caCertificate)), "CA certificate is unavailable");

        MqttConfig config = new MqttConfig(
                brokerUrl,
                clientId,
                TELEMETRY_TOPIC,
                STATE_TOPIC,
                1,
                username,
                password,
                true,
                true,
                caCertificate
        );
        Mqttv3ClientManager manager = config.mqttClientManager();
        MessageProducer telemetry = config.mqttInbound(manager);
        MessageProducer state = config.mqttStateInbound(manager);
        MessageProducer commandResults = config.mqttCommandResultInbound(manager);
        MessageHandler outbound = config.mqttOutbound(manager);

        assertEquals(IDENTITY, manager.getClientId());
        assertSame(manager, inboundManager(telemetry));
        assertSame(manager, inboundManager(state));
        assertSame(manager, inboundManager(commandResults));
        assertSame(manager, outboundManager(outbound));
        assertEquals(IDENTITY, ((MqttPahoMessageHandler) outbound).getClientId());
        assertArrayEquals(new String[]{TARGET}, manager.getConnectionInfo().getServerURIs());
        assertTrue(manager.getConnectionInfo().isAutomaticReconnect());
        assertTrue(manager.getConnectionInfo().isCleanSession());

        try {
            manager.start();
            ((MqttPahoMessageDrivenChannelAdapter) telemetry).start();
            ((MqttPahoMessageDrivenChannelAdapter) state).start();
            ((MqttPahoMessageDrivenChannelAdapter) commandResults).start();

            waitForConnection(manager);
            assertTrue(((MqttPahoMessageDrivenChannelAdapter) telemetry).isRunning());
            assertTrue(((MqttPahoMessageDrivenChannelAdapter) state).isRunning());
            assertTrue(((MqttPahoMessageDrivenChannelAdapter) commandResults).isRunning());

            // Read-only authorization check using the one shared Paho client.
            manager.getClient().subscribe(
                    new String[]{TELEMETRY_TOPIC, STATE_TOPIC, COMMAND_RESULT_TOPIC},
                    new int[]{1, 1, 1}).waitForCompletion(5000);
        } finally {
            ((MqttPahoMessageDrivenChannelAdapter) commandResults).stop();
            ((MqttPahoMessageDrivenChannelAdapter) state).stop();
            ((MqttPahoMessageDrivenChannelAdapter) telemetry).stop();
            manager.stop();
        }
    }

    private void waitForConnection(Mqttv3ClientManager manager) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!manager.isConnected() && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertTrue(manager.isConnected(), "Shared MQTT manager did not connect");
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

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the shared MQTT client test.");
        }
        return value;
    }
}
