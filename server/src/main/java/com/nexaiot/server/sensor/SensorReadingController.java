package com.nexaiot.server.sensor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.nexaiot.server.sensor.dto.CreateSensorReadingRequest;
import com.nexaiot.server.sensor.dto.SensorReadingResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/devices/{deviceId}/readings")
public class SensorReadingController {

    private final SensorReadingService sensorReadingService;

    public SensorReadingController(
        SensorReadingService sensorReadingService
    ) {
        this.sensorReadingService = sensorReadingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SensorReadingResponse createReading(
        @PathVariable String deviceId,
        @Valid @RequestBody CreateSensorReadingRequest request,
        Authentication authentication
    ) {
        return sensorReadingService.createReading(
            deviceId,
            authentication.getName(),
            request
        );
    }

    @GetMapping("/latest")
    public SensorReadingResponse getLatestReading(
        @PathVariable String deviceId,
        Authentication authentication
    ) {
        return sensorReadingService.getLatestReading(
            deviceId,
            authentication.getName()
        );
    }

    @GetMapping
    public List<SensorReadingResponse> getHistory(
        @PathVariable String deviceId,
        @RequestParam(defaultValue = "1h") String range,
        Authentication authentication
    ) {
        return sensorReadingService.getHistory(
            deviceId,
            authentication.getName(),
            range
        );
    }
}
