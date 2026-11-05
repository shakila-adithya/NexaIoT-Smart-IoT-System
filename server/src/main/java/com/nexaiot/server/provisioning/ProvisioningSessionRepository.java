package com.nexaiot.server.provisioning;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProvisioningSessionRepository
        extends MongoRepository<ProvisioningSession, String> {

    Optional<ProvisioningSession> findByDeviceIdAndOwnerEmailAndStatus(
            String deviceId,
            String ownerEmail,
            PairingSessionStatus status
    );

    List<ProvisioningSession> findByDeviceIdAndOwnerEmailOrderByCreatedAtDesc(
            String deviceId,
            String ownerEmail
    );
}
