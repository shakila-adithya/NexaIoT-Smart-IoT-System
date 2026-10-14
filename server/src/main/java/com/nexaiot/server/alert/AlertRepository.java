package com.nexaiot.server.alert;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AlertRepository extends MongoRepository<Alert, String> {

    List<Alert> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);

    List<Alert> findByOwnerEmailAndReadAtIsNull(String ownerEmail);

    Optional<Alert> findByIdAndOwnerEmail(
            String id,
            String ownerEmail
    );

    List<Alert> findByDeviceIdAndOwnerEmailOrderByCreatedAtDesc(
            String deviceId,
            String ownerEmail
    );

    List<Alert> findByDeviceIdAndOwnerEmailAndCreatedAtBetweenOrderByCreatedAtAsc(
            String deviceId, String ownerEmail, java.time.Instant from, java.time.Instant to);

    Optional<Alert> findFirstByDeviceIdAndOwnerEmailAndTypeAndStatusIn(
            String deviceId,
            String ownerEmail,
            AlertType type,
            Set<AlertStatus> statuses
    );

    Optional<Alert> findFirstByDeviceIdAndOwnerEmailAndAlertRuleIdAndStatusIn(
            String deviceId,
            String ownerEmail,
            String alertRuleId,
            Set<AlertStatus> statuses
    );

    long deleteByOwnerEmail(String ownerEmail);
}
