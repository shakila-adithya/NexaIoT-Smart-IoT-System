package com.nexaiot.server.enrollment;

/** Adapter to a production trust source; the default implementation must reject all proof. */
@FunctionalInterface
public interface TrustedActivationVerifier {

    boolean verify(ActivationProof proof);

    static TrustedActivationVerifier rejectAll() {
        return proof -> false;
    }
}
