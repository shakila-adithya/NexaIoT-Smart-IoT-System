package com.nexaiot.server.mqtt.management;

public enum BrokerManagementFailure {
    DISABLED,
    INVALID_CONFIGURATION,
    CONNECTION_FAILED,
    AUTHENTICATION_FAILED,
    COMMAND_REJECTED,
    DUPLICATE_CLIENT,
    DUPLICATE_ROLE,
    TIMEOUT,
    INVALID_RESPONSE,
    UNKNOWN_STATE
}
