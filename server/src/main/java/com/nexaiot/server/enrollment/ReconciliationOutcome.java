package com.nexaiot.server.enrollment;

public enum ReconciliationOutcome {
    CONSISTENT,
    RETRYABLE,
    RECONCILIATION_REQUIRED,
    MANUAL_REVIEW_REQUIRED
}
