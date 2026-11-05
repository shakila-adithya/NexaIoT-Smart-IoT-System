package com.nexaiot.server.simulator;

import java.util.function.BiConsumer;

/** Connection-side abstraction used by the simulator lifecycle and offline fakes. */
public interface SimulatorMqttConnection extends AutoCloseable {

    void connect() throws SimulatorMqttException;

    void subscribe(String exactTopic, BiConsumer<String, byte[]> messageConsumer)
            throws SimulatorMqttException;

    void publish(String exactTopic, byte[] payload, int qos) throws SimulatorMqttException;

    void disconnect() throws SimulatorMqttException;

    boolean isConnected();

    @Override
    void close() throws SimulatorMqttException;
}
