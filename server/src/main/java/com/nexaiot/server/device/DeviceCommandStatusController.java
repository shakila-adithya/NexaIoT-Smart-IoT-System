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
        var result = statusStore.findResult(device.getDeviceKey(), commandId);
        if (result.isPresent()) {
            var value = result.get();
            return ResponseEntity.ok(Map.of(
                    "status", value.status(),
                    "commandId", value.commandId(),
                    "reason", value.reasonOrEmpty(),
                    "confirmedAt", value.processedAt()
            ));
        }
        return statusStore.findPending(device.getDeviceKey(), commandId).isPresent()
                ? ResponseEntity.ok(Map.of("status", "PENDING", "commandId", commandId))
                : ResponseEntity.ok(Map.of("status", "TIMEOUT", "commandId", commandId));
    }
}
