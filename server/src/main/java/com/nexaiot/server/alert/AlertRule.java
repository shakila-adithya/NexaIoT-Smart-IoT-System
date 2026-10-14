package com.nexaiot.server.alert;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "alertRules")
public class AlertRule {

    @Id
    private String id;
    private String ownerEmail;
    private String name;
    private String deviceId;
    private AlertRuleType ruleType;
    private String metricKey;
    private AlertRuleOperator operator;
    private Double threshold;
    private Integer offlineDurationMinutes;
    private AlertSeverity severity;
    private boolean enabled = true;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
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
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
