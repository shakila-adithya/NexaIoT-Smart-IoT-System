package com.nexaiot.server.alert.dto;

import java.time.Instant;

import com.nexaiot.server.alert.AlertRuleOperator;
import com.nexaiot.server.alert.AlertRuleType;
import com.nexaiot.server.alert.AlertSeverity;

public record AlertRuleResponse(
        String id,
        String name,
        String deviceId,
        AlertRuleType ruleType,
        String metricKey,
        AlertRuleOperator operator,
        Double threshold,
        Integer offlineDurationMinutes,
        AlertSeverity severity,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
