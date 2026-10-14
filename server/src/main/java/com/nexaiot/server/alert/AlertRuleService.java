package com.nexaiot.server.alert;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifestAdapter;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceNotFoundException;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.alert.dto.AlertRuleRequest;
import com.nexaiot.server.alert.dto.AlertRuleResponse;

@Service
public class AlertRuleService {

    private static final int MAX_OFFLINE_DURATION_MINUTES = 7 * 24 * 60;

    private final AlertRuleRepository alertRuleRepository;
    private final DeviceRepository deviceRepository;

    public AlertRuleService(
            AlertRuleRepository alertRuleRepository,
            DeviceRepository deviceRepository
    ) {
        this.alertRuleRepository = alertRuleRepository;
        this.deviceRepository = deviceRepository;
    }

    public List<AlertRuleResponse> getRules(String ownerEmail) {
        return alertRuleRepository.findByOwnerEmailOrderByCreatedAtDesc(normalizeEmail(ownerEmail))
                .stream().map(this::toResponse).toList();
    }

    public AlertRuleResponse getRule(String id, String ownerEmail) {
        return toResponse(findOwnedRule(id, ownerEmail));
    }

    public AlertRuleResponse createRule(AlertRuleRequest request, String ownerEmail) {
        String normalizedOwner = normalizeEmail(ownerEmail);
        Device device = findOwnedDevice(request.getDeviceId(), normalizedOwner);
        validate(request, device);

        Instant now = Instant.now();
        AlertRule rule = new AlertRule();
        apply(rule, request, normalizedOwner, now);
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        return toResponse(alertRuleRepository.save(rule));
    }

    public AlertRuleResponse updateRule(String id, AlertRuleRequest request, String ownerEmail) {
        AlertRule rule = findOwnedRule(id, ownerEmail);
        Device device = findOwnedDevice(request.getDeviceId(), rule.getOwnerEmail());
        validate(request, device);
        apply(rule, request, rule.getOwnerEmail(), rule.getCreatedAt());
        rule.setUpdatedAt(Instant.now());
        return toResponse(alertRuleRepository.save(rule));
    }

    public void deleteRule(String id, String ownerEmail) {
        alertRuleRepository.delete(findOwnedRule(id, ownerEmail));
    }

    private void apply(AlertRule rule, AlertRuleRequest request, String ownerEmail, Instant createdAt) {
        rule.setOwnerEmail(ownerEmail);
        rule.setName(request.getName().trim());
        rule.setDeviceId(request.getDeviceId().trim());
        rule.setRuleType(request.getRuleType());
        rule.setSeverity(request.getSeverity());
        rule.setEnabled(request.getEnabled() == null || request.getEnabled());
        rule.setCreatedAt(createdAt);
        if (request.getRuleType() == AlertRuleType.METRIC_THRESHOLD) {
            rule.setMetricKey(request.getMetricKey().trim());
            rule.setOperator(request.getOperator());
            rule.setThreshold(request.getThreshold());
            rule.setOfflineDurationMinutes(null);
        } else {
            rule.setMetricKey(null);
            rule.setOperator(null);
            rule.setThreshold(null);
            rule.setOfflineDurationMinutes(request.getOfflineDurationMinutes());
        }
    }

    private void validate(AlertRuleRequest request, Device device) {
        if (request.getRuleType() == AlertRuleType.METRIC_THRESHOLD) {
            if (request.getMetricKey() == null || request.getMetricKey().isBlank()
                    || request.getOperator() == null || request.getThreshold() == null
                    || !Double.isFinite(request.getThreshold())) {
                throw new IllegalArgumentException("Metric rules require a numeric capability, operator, and threshold.");
            }
            CapabilityDefinition definition = CapabilityManifestAdapter.effectiveManifest(device)
                    .getCapabilities().stream()
                    .filter(capability -> request.getMetricKey().equals(capability.getKey()))
                    .findFirst().orElse(null);
            if (definition == null
                    || definition.getCategory() == CapabilityCategory.CONTROL
                    || definition.getDataType() != CapabilityDataType.NUMBER) {
                throw new IllegalArgumentException("The selected metric is not a numeric measurable capability of this device.");
            }
        } else if (request.getOfflineDurationMinutes() == null
                || request.getOfflineDurationMinutes() <= 0
                || request.getOfflineDurationMinutes() > MAX_OFFLINE_DURATION_MINUTES) {
            throw new IllegalArgumentException("Offline duration must be between 1 minute and 7 days.");
        }
    }

    private Device findOwnedDevice(String id, String ownerEmail) {
        return deviceRepository.findByIdAndOwnerEmail(id.trim(), ownerEmail)
                .orElseThrow(() -> new DeviceNotFoundException("Device not found"));
    }

    private AlertRule findOwnedRule(String id, String ownerEmail) {
        return alertRuleRepository.findByIdAndOwnerEmail(id, normalizeEmail(ownerEmail))
                .orElseThrow(() -> new AlertRuleNotFoundException("Alert rule not found"));
    }

    private AlertRuleResponse toResponse(AlertRule rule) {
        return new AlertRuleResponse(rule.getId(), rule.getName(), rule.getDeviceId(), rule.getRuleType(),
                rule.getMetricKey(), rule.getOperator(), rule.getThreshold(), rule.getOfflineDurationMinutes(),
                rule.getSeverity(), rule.isEnabled(), rule.getCreatedAt(), rule.getUpdatedAt());
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(); }
}
