package com.nexaiot.server.alert;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AlertRuleRepository extends MongoRepository<AlertRule, String> {

    List<AlertRule> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);

    Optional<AlertRule> findByIdAndOwnerEmail(String id, String ownerEmail);

    List<AlertRule> findByDeviceIdAndOwnerEmailAndEnabledTrue(String deviceId, String ownerEmail);

    List<AlertRule> findByDeviceIdAndOwnerEmailAndRuleTypeAndEnabledTrue(
            String deviceId,
            String ownerEmail,
            AlertRuleType ruleType
    );
}
