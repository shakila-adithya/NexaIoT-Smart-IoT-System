package com.nexaiot.server.enrollment;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DeviceEnrollmentOperationRepository
        extends MongoRepository<DeviceEnrollmentOperation, String> {

    Optional<DeviceEnrollmentOperation> findByPairingSessionIdAndOwnerEmail(
            String pairingSessionId,
            String ownerEmail
    );

    Optional<DeviceEnrollmentOperation> findByActiveOperationKey(String activeOperationKey);
}
