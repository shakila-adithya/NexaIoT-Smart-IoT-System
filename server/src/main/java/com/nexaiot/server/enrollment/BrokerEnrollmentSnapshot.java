package com.nexaiot.server.enrollment;

public record BrokerEnrollmentSnapshot(
        boolean clientExists,
        boolean identityMatches,
        boolean roleExists,
        boolean aclComplete,
        boolean credentialStateKnown,
        boolean roleOwnershipMatches,
        boolean inspectionComplete,
        boolean unexpectedResources
) {
    public BrokerEnrollmentSnapshot(
            boolean clientExists,
            boolean identityMatches,
            boolean roleExists,
            boolean aclComplete,
            boolean credentialStateKnown
    ) {
        this(clientExists, identityMatches, roleExists, aclComplete,
                credentialStateKnown, true, true, false);
    }
}
