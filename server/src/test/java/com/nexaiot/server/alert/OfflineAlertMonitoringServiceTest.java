package com.nexaiot.server.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;

class OfflineAlertMonitoringServiceTest {

    @Test
    void customOfflineDurationCreatesOnlyOneUnresolvedAlert() {
        Device device = new Device();
        device.setId("device-1");
        device.setOwnerEmail("owner@example.com");
        device.setDeviceKey("NEXA-1");
        device.setName("Gateway");
        device.setLastSeenAt(Instant.now().minusSeconds(601));

        AlertRule rule = new AlertRule();
        rule.setId("rule-1");
        rule.setDeviceId(device.getId());
        rule.setOwnerEmail(device.getOwnerEmail());
        rule.setRuleType(AlertRuleType.DEVICE_OFFLINE);
        rule.setOfflineDurationMinutes(5);
        rule.setSeverity(AlertSeverity.CRITICAL);
        rule.setName("Gateway offline");

        List<Alert> alerts = new ArrayList<>();
        OfflineAlertMonitoringService service = new OfflineAlertMonitoringService(
                devicesRepository(device), alertsRepository(alerts), rulesRepository(rule), 15
        );

        service.monitorDevices();
        service.monitorDevices();

        assertEquals(1, alerts.size());
        assertEquals("rule-1", alerts.get(0).getAlertRuleId());
        assertEquals(AlertSeverity.CRITICAL, alerts.get(0).getSeverity());
    }

    private DeviceRepository devicesRepository(Device device) {
        return (DeviceRepository) Proxy.newProxyInstance(
                DeviceRepository.class.getClassLoader(), new Class[]{DeviceRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> List.of(device);
                    case "toString" -> "device-repository";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private AlertRuleRepository rulesRepository(AlertRule rule) {
        return (AlertRuleRepository) Proxy.newProxyInstance(
                AlertRuleRepository.class.getClassLoader(), new Class[]{AlertRuleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByDeviceIdAndOwnerEmailAndRuleTypeAndEnabledTrue" -> List.of(rule);
                    case "toString" -> "rule-repository";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private AlertRepository alertsRepository(List<Alert> alerts) {
        return (AlertRepository) Proxy.newProxyInstance(
                AlertRepository.class.getClassLoader(), new Class[]{AlertRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findFirstByDeviceIdAndOwnerEmailAndAlertRuleIdAndStatusIn" -> alerts.stream()
                            .filter(alert -> ruleMatches(alert, args[2]))
                            .findFirst();
                    case "findFirstByDeviceIdAndOwnerEmailAndTypeAndStatusIn" -> Optional.empty();
                    case "save" -> { Alert alert = (Alert) args[0]; alerts.add(alert); yield alert; }
                    case "toString" -> "alert-repository";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private boolean ruleMatches(Alert alert, Object ruleId) {
        return ruleId.equals(alert.getAlertRuleId())
                && Set.of(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED).contains(alert.getStatus());
    }
}
