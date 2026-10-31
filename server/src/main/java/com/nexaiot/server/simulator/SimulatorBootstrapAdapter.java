package com.nexaiot.server.simulator;

import com.nexaiot.server.mqtt.management.GeneratedDeviceCredential;

/**
 * Internal abstraction for future authenticated credential delivery. It is
 * intentionally not a controller contract and exposes no public retrieval API.
 */
public interface SimulatorBootstrapAdapter {

    SimulatorBootstrapGrant authorize(
            SimulatorDeviceIdentity identity,
            String ownerEmail,
            String enrollmentOperationId
    );

    SimulatorCredentialEnvelope deliver(
            SimulatorBootstrapGrant grant,
            SimulatorDeviceIdentity presentedIdentity,
            GeneratedDeviceCredential credential
    );
}
