package com.nexaiot.server.enrollment;

import org.springframework.stereotype.Service;

@Service
public class EnrollmentReconciliationPlanner {

    public ReconciliationPlan classify(
            DeviceEnrollmentOperation operation,
            BrokerEnrollmentSnapshot broker
    ) {
        if (operation == null || broker == null) {
            return new ReconciliationPlan(
                    ReconciliationOutcome.MANUAL_REVIEW_REQUIRED,
                    "Enrollment or broker state is unavailable.");
        }
        if (operation.isReconciliationRequired()) {
            return new ReconciliationPlan(
                    ReconciliationOutcome.RECONCILIATION_REQUIRED,
                    "The enrollment operation recorded an unknown broker outcome.");
        }
        if (broker.clientExists() && !broker.identityMatches()) {
            return new ReconciliationPlan(
                    ReconciliationOutcome.MANUAL_REVIEW_REQUIRED,
                    "A broker identity exists but does not match the operation.");
        }
        if (broker.clientExists() && !broker.roleExists()) {
            return new ReconciliationPlan(
                    ReconciliationOutcome.RECONCILIATION_REQUIRED,
                    "Broker client exists without its owned role.");
        }
        if (broker.roleExists() && !broker.aclComplete()) {
            return new ReconciliationPlan(
                    ReconciliationOutcome.RECONCILIATION_REQUIRED,
                    "Broker role exists with incomplete ACLs.");
        }
        if (operation.getState() == EnrollmentState.REQUESTED
                || operation.getState() == EnrollmentState.BROKER_CLIENT_PENDING) {
            return new ReconciliationPlan(
                    ReconciliationOutcome.RETRYABLE,
                    "No completed broker step is recorded.");
        }
        if (operation.getState() == EnrollmentState.CREDENTIAL_DELIVERY_PENDING
                && !operation.isCredentialDelivered()) {
            return new ReconciliationPlan(
                    ReconciliationOutcome.MANUAL_REVIEW_REQUIRED,
                    "Credential delivery is incomplete and the password cannot be recovered.");
        }
        if (operation.getState() == EnrollmentState.COMPLETED
                && broker.clientExists()
                && broker.identityMatches()
                && broker.roleExists()
                && broker.aclComplete()
                && broker.credentialStateKnown()) {
            return new ReconciliationPlan(
                    ReconciliationOutcome.CONSISTENT,
                    "MongoDB enrollment state matches broker state.");
        }
        return new ReconciliationPlan(
                ReconciliationOutcome.RECONCILIATION_REQUIRED,
                "Enrollment and broker state require controlled review.");
    }
}
