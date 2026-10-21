package com.nexaiot.server.simulator;

@FunctionalInterface
public interface SimulatorActivationEvidenceVerifier {

    boolean verify(SimulatorDeviceIdentity identity, SimulatorActivationEvidence evidence);
}
