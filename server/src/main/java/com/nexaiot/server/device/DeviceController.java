package com.nexaiot.server.device;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexaiot.server.device.dto.ControlDeviceRequest;
import com.nexaiot.server.device.dto.CreateDeviceRequest;
import com.nexaiot.server.device.dto.DeviceResponse;
import com.nexaiot.server.device.dto.UpdateDeviceRequest;
import com.nexaiot.server.mqtt.MqttCommandPublisher;
import com.nexaiot.server.device.diagnostics.DeviceActivityResponse;
import com.nexaiot.server.device.diagnostics.DeviceDiagnosticsResponse;
import com.nexaiot.server.device.diagnostics.DeviceDiagnosticsService;

import jakarta.validation.Valid;
import com.nexaiot.server.security.RateLimitProperties;
import com.nexaiot.server.security.RateLimitService;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final MqttCommandPublisher mqttCommandPublisher;
    private final DeviceDiagnosticsService deviceDiagnosticsService;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;

    public DeviceController(
            DeviceService deviceService,
            MqttCommandPublisher mqttCommandPublisher,
            DeviceDiagnosticsService deviceDiagnosticsService,
            RateLimitService rateLimitService,
            RateLimitProperties rateLimitProperties
    ) {
        this.deviceService = deviceService;
        this.mqttCommandPublisher = mqttCommandPublisher;
        this.deviceDiagnosticsService = deviceDiagnosticsService;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
    }

    @PostMapping
    public ResponseEntity<DeviceResponse> createDevice(
            @Valid @RequestBody CreateDeviceRequest request,
            Authentication authentication
    ) {
        DeviceResponse device = deviceService.createDevice(
                request,
                authentication.getName()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(device);
    }

    @GetMapping
    public ResponseEntity<List<DeviceResponse>> getDevices(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                deviceService.getDevices(authentication.getName())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeviceResponse> getDevice(
            @PathVariable String id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                deviceService.getDevice(
                        id,
                        authentication.getName()
                )
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeviceResponse> updateDevice(
            @PathVariable String id,
            @Valid @RequestBody UpdateDeviceRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                deviceService.updateDevice(
                        id,
                        request,
                        authentication.getName()
                )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDevice(
            @PathVariable String id,
            Authentication authentication
    ) {
        deviceService.deleteDevice(
                id,
                authentication.getName()
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/control")
    public ResponseEntity<Map<String, Object>> controlDevice(
            @PathVariable String id,
            @Valid @RequestBody ControlDeviceRequest request,
            Authentication authentication
    ) {
        DeviceResponse device = deviceService.getControllableDevice(
                id,
                authentication.getName()
        );

        rateLimitService.requireAllowed(
                "device-command-user-device",
                authentication.getName() + ":" + device.getId(),
                rateLimitProperties.getDeviceCommandMax(),
                rateLimitProperties.getDeviceCommandWindowSeconds());

        String payload =
                "{\"power\":" + request.power() + "}";

        com.nexaiot.server.mqtt.MqttPendingCommand pending = mqttCommandPublisher.publishCommand(
                device.getDeviceKey(),
                payload
        );

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "message", "Device command published",
                        "deviceId", device.getId(),
                        "deviceKey", device.getDeviceKey(),
                        "power", request.power(),
                        "commandId", pending.commandId(),
                        "correlationId", pending.correlationId()
                ));
    }

    @GetMapping("/{id}/diagnostics")
    public ResponseEntity<DeviceDiagnosticsResponse> diagnostics(
            @PathVariable String id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(deviceDiagnosticsService.diagnose(id, authentication.getName()));
    }

    @GetMapping("/{id}/activity")
    public ResponseEntity<List<DeviceActivityResponse>> activity(
            @PathVariable String id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(deviceDiagnosticsService.activity(id, authentication.getName()));
    }
}
