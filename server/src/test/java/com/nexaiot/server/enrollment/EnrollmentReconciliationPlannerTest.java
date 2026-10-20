package com.nexaiot.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EnrollmentReconciliationPlannerTest {

    private final EnrollmentReconciliationPlanner planner =
            new EnrollmentReconciliationPlanner();

    @Test
    void identifiesAnUnownedIdentityAsManualReview() {
        DeviceEnrollmentOperation operation = operation(EnrollmentState.BROKER_CLIENT_CREATED);
        ReconciliationPlan plan = planner.classify(
                operation,
                new BrokerEnrollmentSnapshot(true, false, true, true, true));

        assertEquals(ReconciliationOutcome.MANUAL_REVIEW_REQUIRED, plan.outcome());
    }

    @Test
    void identifiesIncompleteAclAsReconciliationRequired() {
        DeviceEnrollmentOperation operation = operation(EnrollmentState.ACL_ASSIGNED);
        ReconciliationPlan plan = planner.classify(
                operation,
                new BrokerEnrollmentSnapshot(true, true, true, false, true));

        assertEquals(ReconciliationOutcome.RECONCILIATION_REQUIRED, plan.outcome());
    }

    @Test
    void identifiesCompleteMatchingStateAsConsistent() {
        DeviceEnrollmentOperation operation = operation(EnrollmentState.COMPLETED);
        ReconciliationPlan plan = planner.classify(
                operation,
                new BrokerEnrollmentSnapshot(true, true, true, true, true));

        assertEquals(ReconciliationOutcome.CONSISTENT, plan.outcome());
    }

    private DeviceEnrollmentOperation operation(EnrollmentState state) {
        DeviceEnrollmentOperation operation = new DeviceEnrollmentOperation();
        operation.setState(state);
        return operation;
    }
}
