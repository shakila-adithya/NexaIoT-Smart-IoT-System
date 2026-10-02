package com.nexaiot.server.device;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DeviceRepository extends MongoRepository<Device, String> {

    List<Device> findByOwnerEmail(String ownerEmail);

    Optional<Device> findByIdAndOwnerEmail(String id, String ownerEmail);

    Optional<Device> findByDeviceKey(String deviceKey);

    boolean existsByDeviceKey(String deviceKey);
}