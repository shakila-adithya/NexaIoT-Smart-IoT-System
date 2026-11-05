package com.nexaiot.server.simulator;

import java.util.function.Consumer;

@FunctionalInterface
public interface SimulatorMqttConnectionFactory {

    SimulatorMqttConnection create(
            SimulatorDeviceIdentity identity,
            SimulatorMqttConfiguration configuration,
            String username,
            String clientId,
            char[] password,
            Consumer<SimulatorMqttFailure> disconnectListener
    ) throws SimulatorMqttException;
}
