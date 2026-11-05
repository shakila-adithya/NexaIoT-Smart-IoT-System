package com.nexaiot.server.simulator;

public class SimulatorMqttException extends Exception {

    private final SimulatorMqttFailure failure;

    public SimulatorMqttException(SimulatorMqttFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public SimulatorMqttException(
            SimulatorMqttFailure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public SimulatorMqttFailure failure() {
        return failure;
    }
}
