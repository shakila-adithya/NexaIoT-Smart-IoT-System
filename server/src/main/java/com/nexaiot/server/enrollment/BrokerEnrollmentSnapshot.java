package com.nexaiot.server.enrollment;

public record BrokerEnrollmentSnapshot(
        boolean clientExists,
        boolean identityMatches,
        boolean roleExists,
        boolean aclComplete,
        boolean credentialStateKnown
) {
}
