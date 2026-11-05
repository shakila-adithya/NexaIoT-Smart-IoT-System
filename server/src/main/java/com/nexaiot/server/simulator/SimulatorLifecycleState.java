package com.nexaiot.server.simulator;

public enum SimulatorLifecycleState {
    REGISTERED,
    PAIRING,
    CLAIMED,
    ENROLLMENT_AUTHORIZED,
    BROKER_PROVISIONED,
    CREDENTIALS_DELIVERED,
    CONNECTED,
    ACTIVE,
    DISCONNECTED,
    REVOKED
}
