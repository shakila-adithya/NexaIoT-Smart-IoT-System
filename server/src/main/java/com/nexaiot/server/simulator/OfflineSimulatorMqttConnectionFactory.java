package com.nexaiot.server.simulator;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** In-memory transport used by the standalone simulator's safe default mode. */
public final class OfflineSimulatorMqttConnectionFactory implements SimulatorMqttConnectionFactory {

    private final OfflineSimulatorMqttConnection connection = new OfflineSimulatorMqttConnection();

    @Override
    public SimulatorMqttConnection create(SimulatorDeviceIdentity identity,
            SimulatorMqttConfiguration configuration, String username, String clientId,
            char[] password, Consumer<SimulatorMqttFailure> disconnectListener) {
        connection.disconnectListener = disconnectListener;
        return connection;
    }

    public void emitCommand(String commandId, boolean power) {
        if (connection.commandConsumer == null) {
            throw new IllegalStateException("Offline simulator is not subscribed yet.");
        }
        String payload = "{\"commandId\":\"" + commandId
                + "\",\"correlationId\":\"offline-demo\",\"capabilityKey\":\"power\","
                + "\"value\":" + power + ",\"issuedAt\":\"" + Instant.now() + "\"}";
        connection.commandConsumer.accept(connection.commandTopic,
                payload.getBytes(StandardCharsets.UTF_8));
    }

    private static final class OfflineSimulatorMqttConnection implements SimulatorMqttConnection {
        private Consumer<SimulatorMqttFailure> disconnectListener = failure -> { };
        private BiConsumer<String, byte[]> commandConsumer;
        private String commandTopic;
        private boolean connected;

        @Override public void connect() { connected = true; }

        @Override public void subscribe(String exactTopic, BiConsumer<String, byte[]> consumer) {
            commandTopic = exactTopic;
            commandConsumer = consumer;
        }

        @Override public void publish(String exactTopic, byte[] payload, int qos) {
            System.out.println("[offline] publish topic=" + exactTopic + " bytes=" + payload.length);
        }

        @Override public void disconnect() {
            connected = false;
            disconnectListener.accept(SimulatorMqttFailure.UNEXPECTED_DISCONNECT);
        }

        @Override public boolean isConnected() { return connected; }
        @Override public void close() { connected = false; }
    }
}
