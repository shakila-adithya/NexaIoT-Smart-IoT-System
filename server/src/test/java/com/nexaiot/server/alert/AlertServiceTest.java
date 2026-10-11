package com.nexaiot.server.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class AlertServiceTest {

    @Test
    void marksOneAlertReadIdempotentlyAndScopesByOwner() {
        Alert alert = alert("alert-1", "owner@example.com");
        AtomicInteger saves = new AtomicInteger();
        AlertService service = new AlertService(repository(alert, List.of(), saves));

        assertNotNull(service.markAlertRead("alert-1", "OWNER@example.com").readAt());
        Instant firstReadAt = alert.getReadAt();
        service.markAlertRead("alert-1", "owner@example.com");

        assertEquals(firstReadAt, alert.getReadAt());
        assertEquals(1, saves.get());
        assertThrows(AlertNotFoundException.class,
                () -> service.markAlertRead("alert-1", "other@example.com"));
    }

    @Test
    void marksOnlyUnreadOwnedAlertsWithoutChangingLifecycleStatus() {
        Alert unread = alert("alert-1", "owner@example.com");
        unread.setStatus(AlertStatus.ACTIVE);
        Alert alreadyRead = alert("alert-2", "owner@example.com");
        alreadyRead.setStatus(AlertStatus.RESOLVED);
        alreadyRead.setReadAt(Instant.now());

        AlertService service = new AlertService(
                repository(unread, List.of(unread), new AtomicInteger())
        );

        var updated = service.markAllAlertsRead("owner@example.com");

        assertEquals(1, updated.size());
        assertTrue(updated.get(0).readAt() != null);
        assertEquals(AlertStatus.ACTIVE, unread.getStatus());
    }

    private Alert alert(String id, String ownerEmail) {
        Alert alert = new Alert();
        alert.setId(id);
        alert.setOwnerEmail(ownerEmail);
        alert.setType(AlertType.TEMPERATURE_HIGH);
        alert.setSeverity(AlertSeverity.WARNING);
        alert.setStatus(AlertStatus.ACTIVE);
        alert.setCreatedAt(Instant.now());
        return alert;
    }

    private AlertRepository repository(
            Alert ownedAlert,
            List<Alert> unreadAlerts,
            AtomicInteger saves
    ) {
        return (AlertRepository) Proxy.newProxyInstance(
                AlertRepository.class.getClassLoader(),
                new Class[]{AlertRepository.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "findByIdAndOwnerEmail" -> {
                            String owner = (String) args[1];
                            yield owner.equals("owner@example.com")
                                    ? Optional.of(ownedAlert)
                                    : Optional.empty();
                        }
                        case "findByOwnerEmailAndReadAtIsNull" -> unreadAlerts;
                        case "save" -> {
                            saves.incrementAndGet();
                            yield args[0];
                        }
                        case "saveAll" -> args[0];
                        case "toString" -> "alert-repository";
                        case "hashCode" -> 1;
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                }
        );
    }
}
