package com.nexaiot.server.alert;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.nexaiot.server.alert.dto.AlertResponse;

@Service
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public List<AlertResponse> getAlerts(String ownerEmail) {
        return alertRepository
                .findByOwnerEmailOrderByCreatedAtDesc(
                        normalizeEmail(ownerEmail)
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public AlertResponse getAlert(
            String alertId,
            String ownerEmail
    ) {
        return toResponse(findOwnedAlert(alertId, ownerEmail));
    }

    public AlertResponse acknowledgeAlert(
            String alertId,
            String ownerEmail
    ) {
        Alert alert = findOwnedAlert(alertId, ownerEmail);

        if (alert.getStatus() == AlertStatus.ACTIVE) {
            alert.setStatus(AlertStatus.ACKNOWLEDGED);
            alert.setAcknowledgedAt(Instant.now());
            alert = alertRepository.save(alert);
        }

        return toResponse(alert);
    }

    public AlertResponse resolveAlert(
            String alertId,
            String ownerEmail
    ) {
        Alert alert = findOwnedAlert(alertId, ownerEmail);

        if (alert.getStatus() == AlertStatus.ACTIVE
                || alert.getStatus() == AlertStatus.ACKNOWLEDGED) {
            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(Instant.now());
            alert = alertRepository.save(alert);
        }

        return toResponse(alert);
    }

    public void deleteAlert(
            String alertId,
            String ownerEmail
    ) {
        Alert alert = findOwnedAlert(alertId, ownerEmail);

        alertRepository.delete(alert);
    }

    private Alert findOwnedAlert(
            String alertId,
            String ownerEmail
    ) {
        return alertRepository
                .findByIdAndOwnerEmail(
                        alertId,
                        normalizeEmail(ownerEmail)
                )
                .orElseThrow(
                        () -> new AlertNotFoundException(
                                "Alert not found"
                        )
                );
    }

    private AlertResponse toResponse(Alert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getDeviceId(),
                alert.getDeviceKey(),
                alert.getDeviceName(),
                alert.getType(),
                alert.getSeverity(),
                alert.getMessage(),
                alert.getValue(),
                alert.getThreshold(),
                alert.getStatus(),
                alert.getCreatedAt(),
                alert.getAcknowledgedAt(),
                alert.getResolvedAt()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
