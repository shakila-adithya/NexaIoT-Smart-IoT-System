package com.nexaiot.server.enrollment;

import java.util.EnumSet;
import java.util.Set;

public enum EnrollmentState {
    REQUESTED,
    BROKER_CLIENT_PENDING,
    BROKER_CLIENT_CREATED,
    ACL_PENDING,
    ACL_ASSIGNED,
    BROKER_VERIFIED,
    CREDENTIAL_DELIVERY_PENDING,
    DEVICE_VERIFICATION_PENDING,
    COMPLETED,
    FAILED,
    RECONCILIATION_REQUIRED,
    REVOKE_PENDING,
    CANCELLED;

    public boolean canTransitionTo(EnrollmentState target) {
        if (target == null || this == target) {
            return false;
        }
        return allowedTransitions().contains(target);
    }

    private Set<EnrollmentState> allowedTransitions() {
        return switch (this) {
            case REQUESTED -> EnumSet.of(BROKER_CLIENT_PENDING, CANCELLED);
            case BROKER_CLIENT_PENDING -> EnumSet.of(
                    BROKER_CLIENT_CREATED, FAILED, RECONCILIATION_REQUIRED, CANCELLED);
            case BROKER_CLIENT_CREATED -> EnumSet.of(
                    ACL_PENDING, FAILED, RECONCILIATION_REQUIRED);
            case ACL_PENDING -> EnumSet.of(
                    ACL_ASSIGNED, FAILED, RECONCILIATION_REQUIRED);
            case ACL_ASSIGNED -> EnumSet.of(
                    BROKER_VERIFIED, FAILED, RECONCILIATION_REQUIRED);
            case BROKER_VERIFIED -> EnumSet.of(
                    CREDENTIAL_DELIVERY_PENDING, FAILED, RECONCILIATION_REQUIRED);
            case CREDENTIAL_DELIVERY_PENDING -> EnumSet.of(
                    DEVICE_VERIFICATION_PENDING, FAILED, RECONCILIATION_REQUIRED);
            case DEVICE_VERIFICATION_PENDING -> EnumSet.of(
                    COMPLETED, FAILED, RECONCILIATION_REQUIRED);
            case FAILED -> EnumSet.of(RECONCILIATION_REQUIRED, CANCELLED);
            case RECONCILIATION_REQUIRED -> EnumSet.of(
                    BROKER_CLIENT_PENDING, ACL_PENDING, REVOKE_PENDING, CANCELLED);
            case COMPLETED -> EnumSet.of(REVOKE_PENDING);
            case REVOKE_PENDING -> EnumSet.of(RECONCILIATION_REQUIRED, CANCELLED);
            case CANCELLED -> EnumSet.noneOf(EnrollmentState.class);
        };
    }
}
