package com.nexaiot.server.simulator;

import java.util.Objects;

/**
 * In-memory lifecycle contract for simulator fixtures. It deliberately does
 * not persist device state or mark a device active from broker provisioning.
 */
public final class SimulatorLifecycle {

    private SimulatorLifecycleState state = SimulatorLifecycleState.REGISTERED;

    public synchronized SimulatorLifecycleState state() {
        return state;
    }

    public synchronized void pairingStarted() {
        require(SimulatorLifecycleState.REGISTERED);
        state = SimulatorLifecycleState.PAIRING;
    }

    public synchronized void pairingClaimed() {
        require(SimulatorLifecycleState.PAIRING);
        state = SimulatorLifecycleState.CLAIMED;
    }

    public synchronized void enrollmentAuthorized() {
        require(SimulatorLifecycleState.CLAIMED);
        state = SimulatorLifecycleState.ENROLLMENT_AUTHORIZED;
    }

    public synchronized void brokerProvisioned() {
        require(SimulatorLifecycleState.ENROLLMENT_AUTHORIZED);
        state = SimulatorLifecycleState.BROKER_PROVISIONED;
    }

    public synchronized void credentialsDelivered() {
        require(SimulatorLifecycleState.BROKER_PROVISIONED);
        state = SimulatorLifecycleState.CREDENTIALS_DELIVERED;
    }

    public synchronized void connected() {
        require(SimulatorLifecycleState.CREDENTIALS_DELIVERED);
        state = SimulatorLifecycleState.CONNECTED;
    }

    public synchronized void activationVerified() {
        require(SimulatorLifecycleState.CONNECTED);
        state = SimulatorLifecycleState.ACTIVE;
    }

    public synchronized void disconnected() {
        if (state != SimulatorLifecycleState.ACTIVE
                && state != SimulatorLifecycleState.CONNECTED) {
            throw new IllegalStateException("Simulator is not connected.");
        }
        state = SimulatorLifecycleState.DISCONNECTED;
    }

    public synchronized void reconnected() {
        require(SimulatorLifecycleState.DISCONNECTED);
        state = SimulatorLifecycleState.CONNECTED;
    }

    public synchronized void revoked() {
        if (state == SimulatorLifecycleState.REVOKED) {
            return;
        }
        state = SimulatorLifecycleState.REVOKED;
    }

    public synchronized void requireConnectable() {
        if (state != SimulatorLifecycleState.CREDENTIALS_DELIVERED
                && state != SimulatorLifecycleState.DISCONNECTED) {
            throw new IllegalStateException("Simulator credentials are not ready for connection.");
        }
    }

    private void require(SimulatorLifecycleState expected) {
        if (!Objects.equals(state, expected)) {
            throw new IllegalStateException(
                    "Invalid simulator lifecycle transition from " + state + ".");
        }
    }
}
