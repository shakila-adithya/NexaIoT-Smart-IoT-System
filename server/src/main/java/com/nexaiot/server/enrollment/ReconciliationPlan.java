package com.nexaiot.server.enrollment;

public record ReconciliationPlan(
        ReconciliationOutcome outcome,
        String reason
) {
}
