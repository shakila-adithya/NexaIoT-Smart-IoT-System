package com.nexaiot.server.mqtt.management;

public class BrokerManagementException extends RuntimeException {

    private final BrokerManagementFailure failure;

    public BrokerManagementException(BrokerManagementFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public BrokerManagementException(
            BrokerManagementFailure failure,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failure = failure;
    }

    public BrokerManagementFailure getFailure() {
        return failure;
    }
}
