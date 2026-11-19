package com.nexaiot.server.device;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.nexaiot.server.service.AccountDeletionService;

class DeviceDeletionAuthorizationTest {
    private final DeviceRepository repository = mock(DeviceRepository.class);
    private final AccountDeletionService deletion = mock(AccountDeletionService.class);
    private final DeviceService service = new DeviceService(repository, null, deletion);

    @Test
    void ownerDeletionUsesSharedCleanupInsteadOfDeletingOnlyTheDevice() {
        Device device = new Device();
        device.setId("device-1");
        when(repository.findByIdAndOwnerEmail("device-1", "owner@example.com"))
                .thenReturn(Optional.of(device));
        service.deleteDevice("device-1", " OWNER@example.com ");
        verify(deletion).deleteDevice("device-1");
        verify(repository, never()).delete(any(Device.class));
    }

    @Test
    void anotherOwnerCannotTriggerCleanupOrBrokerRevocation() {
        when(repository.findByIdAndOwnerEmail("device-1", "other@example.com"))
                .thenReturn(Optional.empty());
        assertThrows(DeviceNotFoundException.class,
                () -> service.deleteDevice("device-1", "other@example.com"));
        verifyNoInteractions(deletion);
        verify(repository, never()).delete(any(Device.class));
    }
}
