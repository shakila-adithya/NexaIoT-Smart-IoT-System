package com.nexaiot.server.enrollment;

public enum ReconciliationOutcome {
    CONSISTENT,
    SAFE_TO_CONTINUE,
    RETRYABLE,
    CREDENTIAL_REISSUANCE_REQUIRED,
    RECONCILIATION_REQUIRED,
    MANUAL_REVIEW_REQUIRED,
    TERMINAL_FAILURE
}
