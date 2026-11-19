package com.nexaiot.server.mqtt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import com.nexaiot.server.device.DeviceCommandStatusController;
import com.nexaiot.server.device.DeviceService;
import com.nexaiot.server.device.DeviceNotFoundException;
import com.nexaiot.server.device.dto.DeviceResponse;

class DeviceCommandStatusControllerTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private final DeviceService devices = mock(DeviceService.class);
    private final Authentication authentication = mock(Authentication.class);
    private final InMemoryMqttCommandStatusStoreTest.MutableClock clock = new InMemoryMqttCommandStatusStoreTest.MutableClock();
    private final InMemoryMqttCommandStatusStore store = new InMemoryMqttCommandStatusStore(clock, 4);
    private final DeviceCommandStatusController controller = new DeviceCommandStatusController(devices, store);

    private void prepare() {
        when(authentication.getName()).thenReturn("owner@example.com");
        DeviceResponse device = mock(DeviceResponse.class);
        when(device.getDeviceKey()).thenReturn("device");
        when(devices.getDevice("id", "owner@example.com")).thenReturn(device);
        store.registerPending(new MqttPendingCommand("device", "one", "corr", "power", START, START.plusSeconds(30)));
    }

    @Test void pendingResponseProvidesDeadlineAndServerRemainingTime() {
        prepare();
        clock.now = START.plusSeconds(5);
        var body = controller.status("id", "one", authentication).getBody();
        assertEquals("PENDING", body.get("status"));
        assertEquals(25_000L, body.get("remainingMs"));
        assertEquals(START.plusSeconds(30), body.get("expiresAt"));
    }

    @Test void unknownAndExpiredCommandsHaveDifferentResponses() {
        prepare();
        assertEquals("UNKNOWN", controller.status("id", "missing", authentication).getBody().get("status"));
        clock.now = START.plusSeconds(30);
        var body = controller.status("id", "one", authentication).getBody();
        assertEquals("TIMEOUT", body.get("status"));
        assertEquals(0L, body.get("remainingMs"));
        assertFalse(body.containsKey("confirmedAt"));
    }

    @Test void ownerCheckHappensBeforeCommandStatusIsRead() {
        MqttCommandStatusStore isolated = mock(MqttCommandStatusStore.class);
        when(authentication.getName()).thenReturn("other@example.com");
        when(devices.getDevice("id", "other@example.com")).thenThrow(new DeviceNotFoundException("Device not found"));
        assertThrows(DeviceNotFoundException.class,
                () -> new DeviceCommandStatusController(devices, isolated).status("id", "one", authentication));
        verifyNoInteractions(isolated);
    }
}
