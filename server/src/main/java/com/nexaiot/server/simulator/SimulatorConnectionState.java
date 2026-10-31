package com.nexaiot.server.simulator;

public enum SimulatorConnectionState {
    BOOTSTRAP_PENDING,
    CREDENTIALS_AVAILABLE,
    CONNECTING,
    CONNECTED,
    ACTIVATION_PENDING,
    ACTIVE,
    DISCONNECTED,
    RECONNECTING,
    FAILED,
    REVOKED,
    STOPPED
}
