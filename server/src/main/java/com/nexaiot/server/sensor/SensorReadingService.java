package com.nexaiot.server.sensor;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceNotFoundException;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.sensor.dto.CreateSensorReadingRequest;
import com.nexaiot.server.sensor.dto.SensorReadingResponse;

@Service
public class SensorReadingService {

    private final SensorReadingRepository sensorReadingRepository;
    private final DeviceRepository deviceRepository;

    public SensorReadingService(
        SensorReadingRepository sensorReadingRepository,
        DeviceRepository deviceRepository
    ) {
        this.sensorReadingRepository = sensorReadingRepository;
        this.deviceRepository = deviceRepository;
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
        String ownerEmail
    ) {
        getOwnedDevice(deviceId, ownerEmail);

        return sensorReadingRepository
            .findTop100ByDeviceIdOrderByReceivedAtDesc(deviceId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private SensorReadingResponse saveReading(
        Device device,
        CreateSensorReadingRequest request
    ) {
        SensorReading reading = new SensorReading();

        reading.setDeviceId(device.getId());
        reading.setDeviceKey(device.getDeviceKey());
        reading.setMetrics(request.metrics());
        reading.setBattery(request.battery());
        reading.setRssi(request.rssi());
        reading.setDeviceTimestamp(request.timestamp());
        reading.setReceivedAt(Instant.now());

        SensorReading saved =
            sensorReadingRepository.save(reading);

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