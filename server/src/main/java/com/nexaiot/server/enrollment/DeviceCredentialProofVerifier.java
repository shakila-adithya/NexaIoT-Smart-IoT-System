package com.nexaiot.server.enrollment;

/** Trust-anchor adapter; production implementations must verify possession cryptographically. */
@FunctionalInterface
public interface DeviceCredentialProofVerifier {

    boolean verify(DeviceCredentialProof proof);

    static DeviceCredentialProofVerifier rejectAll() {
        return proof -> false;
    }
}
