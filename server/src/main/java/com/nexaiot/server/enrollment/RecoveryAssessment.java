package com.nexaiot.server.enrollment;

public record RecoveryAssessment(
        ReconciliationOutcome outcome,
        String reason,
        boolean retrySafe,
        boolean manualActionRequired
) {
}
