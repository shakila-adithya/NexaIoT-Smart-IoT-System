package com.nexaiot.server.alert.dto;

import com.nexaiot.server.alert.AlertRuleOperator;
import com.nexaiot.server.alert.AlertRuleType;
import com.nexaiot.server.alert.AlertSeverity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AlertRuleRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    private String deviceId;

    @NotNull
    private AlertRuleType ruleType;

    private String metricKey;
    private AlertRuleOperator operator;
    private Double threshold;
    private Integer offlineDurationMinutes;

    @NotNull
    private AlertSeverity severity;

    private Boolean enabled;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public AlertRuleType getRuleType() { return ruleType; }
    public void setRuleType(AlertRuleType ruleType) { this.ruleType = ruleType; }
    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }
    public AlertRuleOperator getOperator() { return operator; }
    public void setOperator(AlertRuleOperator operator) { this.operator = operator; }
    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }
    public Integer getOfflineDurationMinutes() { return offlineDurationMinutes; }
    public void setOfflineDurationMinutes(Integer offlineDurationMinutes) { this.offlineDurationMinutes = offlineDurationMinutes; }
    public AlertSeverity getSeverity() { return severity; }
    public void setSeverity(AlertSeverity severity) { this.severity = severity; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
