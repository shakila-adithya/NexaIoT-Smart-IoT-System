package com.nexaiot.server.sensor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SensorReadingRepository
        extends MongoRepository<SensorReading, String> {

    Optional<SensorReading>
        findTopByDeviceIdOrderByReceivedAtDesc(String deviceId);

    List<SensorReading>
        findTop100ByDeviceIdOrderByReceivedAtDesc(String deviceId);

    List<SensorReading>
        findByDeviceIdAndReceivedAtBetweenOrderByReceivedAtAsc(
            String deviceId,
            Instant from,
            Instant to
        );

    void deleteByDeviceId(String deviceId);
}