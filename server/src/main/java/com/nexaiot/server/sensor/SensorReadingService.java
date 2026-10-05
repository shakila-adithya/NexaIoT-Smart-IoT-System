package com.nexaiot.server.sensor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.nexaiot.server.alert.AlertEvaluationService;
import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceNotFoundException;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.sensor.dto.CreateSensorReadingRequest;
import com.nexaiot.server.sensor.dto.SensorReadingResponse;

@Service
public class SensorReadingService {

    private static final int MAX_HISTORY_POINTS = 200;

    private final SensorReadingRepository sensorReadingRepository;
    private final DeviceRepository deviceRepository;
    private final AlertEvaluationService alertEvaluationService;

    public SensorReadingService(
        SensorReadingRepository sensorReadingRepository,
        DeviceRepository deviceRepository,
        AlertEvaluationService alertEvaluationService
    ) {
        this.sensorReadingRepository = sensorReadingRepository;
        this.deviceRepository = deviceRepository;
        this.alertEvaluationService = alertEvaluationService;
    }

    public SensorReadingResponse createReading(
        String deviceId,
        String ownerEmail,
        CreateSensorReadingRequest request
    ) {
        Device device = getOwnedDevice(deviceId, ownerEmail);

        return saveReading(device, request);
    }

    public SensorReadingResponse createReadingFromDeviceKey(
        String deviceKey,
        CreateSensorReadingRequest request
    ) {
        Device device = deviceRepository
            .findByDeviceKey(deviceKey)
            .orElseThrow(
                () -> new DeviceNotFoundException(
                    "Device not found for key: " + deviceKey
                )
            );

        return saveReading(device, request);
    }

    public SensorReadingResponse getLatestReading(
        String deviceId,
        String ownerEmail
    ) {
        getOwnedDevice(deviceId, ownerEmail);

        return sensorReadingRepository
            .findTopByDeviceIdOrderByReceivedAtDesc(deviceId)
            .map(this::toResponse)
            .orElse(null);
    }

    public List<SensorReadingResponse> getHistory(
        String deviceId,
        String ownerEmail,
        String range
    ) {
        getOwnedDevice(deviceId, ownerEmail);

        Instant to = Instant.now();
        Instant from = to.minus(getRangeDuration(range));

        List<SensorReading> readings = sensorReadingRepository
            .findByDeviceIdAndReceivedAtBetweenOrderByReceivedAtAsc(
                deviceId,
                from,
                to
            );

        return downsample(readings)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private Duration getRangeDuration(String range) {
        return switch (range) {
            case "1h" -> Duration.ofHours(1);
            case "6h" -> Duration.ofHours(6);
            case "24h" -> Duration.ofHours(24);
            case "7d" -> Duration.ofDays(7);
            default -> throw new IllegalArgumentException(
                "Unsupported history range. Use 1h, 6h, 24h, or 7d."
            );
        };
    }

    private List<SensorReading> downsample(List<SensorReading> readings) {
        if (readings.size() <= MAX_HISTORY_POINTS) {
            return readings;
        }

        List<SensorReading> sampled = new ArrayList<>(MAX_HISTORY_POINTS);
        double step = (double) (readings.size() - 1) / (MAX_HISTORY_POINTS - 1);

        for (int index = 0; index < MAX_HISTORY_POINTS; index++) {
            int sourceIndex = (int) Math.round(index * step);
            sampled.add(readings.get(sourceIndex));
        }

        return sampled;
    }

    private SensorReadingResponse saveReading(
        Device device,
        CreateSensorReadingRequest request
    ) {
        Instant now = Instant.now();

        device.setLastSeenAt(now);
        deviceRepository.save(device);

        SensorReading reading = new SensorReading();

        reading.setDeviceId(device.getId());
        reading.setDeviceKey(device.getDeviceKey());
        reading.setMetrics(request.metrics());
        reading.setBattery(request.battery());
        reading.setRssi(request.rssi());
        reading.setDeviceTimestamp(request.timestamp());
        reading.setReceivedAt(now);

        SensorReading saved =
            sensorReadingRepository.save(reading);

        alertEvaluationService.evaluate(device, saved);

        return toResponse(saved);
    }

    private Device getOwnedDevice(
        String deviceId,
        String ownerEmail
    ) {
        return deviceRepository
            .findByIdAndOwnerEmail(deviceId, ownerEmail)
            .orElseThrow(
                () -> new DeviceNotFoundException(
                    "Device not found."
                )
            );
    }

    private SensorReadingResponse toResponse(
        SensorReading reading
    ) {
        return new SensorReadingResponse(
            reading.getId(),
            reading.getDeviceId(),
            reading.getDeviceKey(),
            reading.getMetrics(),
            reading.getBattery(),
            reading.getRssi(),
            reading.getDeviceTimestamp(),
            reading.getReceivedAt()
        );
    }
}
