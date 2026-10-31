package com.nexaiot.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EnrollmentStateTest {

    @Test
    void acceptsOnlyTheDefinedHappyPathTransitions() {
        assertTrue(EnrollmentState.REQUESTED
                .canTransitionTo(EnrollmentState.BROKER_CLIENT_PENDING));
        assertTrue(EnrollmentState.BROKER_CLIENT_CREATED
                .canTransitionTo(EnrollmentState.ACL_PENDING));
        assertTrue(EnrollmentState.ACL_ASSIGNED
                .canTransitionTo(EnrollmentState.BROKER_VERIFIED));
        assertTrue(EnrollmentState.DEVICE_VERIFICATION_PENDING
                .canTransitionTo(EnrollmentState.COMPLETED));
    }

    @Test
    void rejectsPrematureCompletionAndTerminalReuse() {
        assertFalse(EnrollmentState.REQUESTED.canTransitionTo(EnrollmentState.COMPLETED));
        assertFalse(EnrollmentState.CANCELLED.canTransitionTo(EnrollmentState.REQUESTED));
        assertFalse(EnrollmentState.COMPLETED.canTransitionTo(EnrollmentState.COMPLETED));
    }
}
