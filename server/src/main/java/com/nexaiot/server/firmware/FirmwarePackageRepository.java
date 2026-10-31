package com.nexaiot.server.firmware;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface FirmwarePackageRepository extends MongoRepository<FirmwarePackage, String> {
    List<FirmwarePackage> findAllByOrderByUploadedAtDesc();
}
