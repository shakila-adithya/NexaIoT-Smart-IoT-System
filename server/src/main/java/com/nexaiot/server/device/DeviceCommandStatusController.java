package com.nexaiot.server.device;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexaiot.server.device.dto.DeviceResponse;
import com.nexaiot.server.mqtt.MqttCommandStatusStore;

@RestController
@RequestMapping("/api/devices")
public class DeviceCommandStatusController {

    private final DeviceService deviceService;
    private final MqttCommandStatusStore statusStore;

    public DeviceCommandStatusController(DeviceService deviceService, MqttCommandStatusStore statusStore) {
        this.deviceService = deviceService;
        this.statusStore = statusStore;
    }

    @GetMapping("/{id}/commands/{commandId}")
    public ResponseEntity<Map<String, Object>> status(
            @PathVariable String id,
            @PathVariable String commandId,
            Authentication authentication
    ) {
        DeviceResponse device = deviceService.getDevice(id, authentication.getName());
        var retained = statusStore.findStatus(device.getDeviceKey(), commandId);
        if (retained.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "UNKNOWN", "commandId", commandId,
                    "reason", "This command is unknown or no longer retained. Its outcome cannot be confirmed."));
        }
        var snapshot = retained.get();
        var response = new java.util.LinkedHashMap<String, Object>();
        response.put("status", snapshot.status());
        response.put("commandId", snapshot.command().commandId());
        response.put("issuedAt", snapshot.command().issuedAt());
        response.put("expiresAt", snapshot.command().expiresAt());
        response.put("remainingMs", Math.max(0, java.time.Duration.between(
                snapshot.observedAt(), snapshot.command().expiresAt()).toMillis()));
        if (snapshot.result() != null) {
            response.put("reason", snapshot.result().reasonOrEmpty());
            if (!"TIMEOUT".equals(snapshot.status())) {
                response.put("confirmedAt", snapshot.result().processedAt());
            }
        }
        return ResponseEntity.ok(response);
    }
}
