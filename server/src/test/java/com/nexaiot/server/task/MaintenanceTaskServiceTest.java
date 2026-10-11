package com.nexaiot.server.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.nexaiot.server.alert.Alert;
import com.nexaiot.server.alert.AlertRepository;
import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.task.dto.MaintenanceTaskRequest;

class MaintenanceTaskServiceTest {
    @Test
    void createsAndUpdatesOwnedTaskWithCompletionTimestamp() {
        Device device = device("device-1", "owner@example.com");
        Alert alert = alert("alert-1", "owner@example.com", "device-1");
        List<MaintenanceTask> saved = new ArrayList<>();
        MaintenanceTaskService service = service(saved, device, alert);

        MaintenanceTask created = service.create(request("device-1", "alert-1", TaskStatus.OPEN), "OWNER@example.com") == null ? null : saved.get(0);
        assertEquals("owner@example.com", created.getOwnerEmail());
        assertEquals("alert-1", created.getAlertId());

        MaintenanceTaskRequest completed = request("device-1", "alert-1", TaskStatus.COMPLETED);
        completed.setStatus(TaskStatus.IN_PROGRESS);
        service.update(created.getId(), completed, "owner@example.com");
        completed.setStatus(TaskStatus.COMPLETED);
        var result = service.update(created.getId(), completed, "owner@example.com");
        assertEquals(TaskStatus.COMPLETED, result.status());
        org.junit.jupiter.api.Assertions.assertNotNull(result.completedAt());
    }

    @Test
    void rejectsOtherOwnersAndMismatchedAlerts() {
        Device device = device("device-1", "owner@example.com");
        Alert otherAlert = alert("alert-1", "other@example.com", "device-1");
        MaintenanceTaskService service = service(new ArrayList<>(), device, otherAlert);

        assertThrows(RuntimeException.class, () -> service.create(request("device-1", "alert-1", TaskStatus.OPEN), "owner@example.com"));
        assertThrows(RuntimeException.class, () -> service.create(request("device-1", null, TaskStatus.OPEN), "other@example.com"));
    }

    @Test
    void rejectsInvalidTransitionsAndCrossDeviceAlertLinks() {
        Device device = device("device-1", "owner@example.com");
        Alert mismatched = alert("alert-1", "owner@example.com", "device-2");
        MaintenanceTaskService service = service(new ArrayList<>(), device, mismatched);
        assertThrows(IllegalArgumentException.class, () -> service.create(request("device-1", "alert-1", TaskStatus.OPEN), "owner@example.com"));
    }

    private MaintenanceTaskService service(List<MaintenanceTask> saved, Device device, Alert alert) {
        MaintenanceTaskRepository tasks = (MaintenanceTaskRepository) Proxy.newProxyInstance(
                MaintenanceTaskRepository.class.getClassLoader(), new Class[]{MaintenanceTaskRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> { MaintenanceTask task = (MaintenanceTask) args[0]; if (task.getId() == null) task.setId("task-1"); if (!saved.contains(task)) saved.add(task); yield task; }
                    case "findByIdAndOwnerEmail" -> saved.stream().filter(item -> item.getId().equals(args[0]) && item.getOwnerEmail().equals(args[1])).findFirst();
                    case "delete" -> { saved.remove(args[0]); yield null; }
                    case "findByOwnerEmailOrderByCreatedAtDesc" -> saved.stream().filter(item -> item.getOwnerEmail().equals(args[0])).toList();
                    case "toString" -> "task-repository";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        DeviceRepository devices = (DeviceRepository) Proxy.newProxyInstance(DeviceRepository.class.getClassLoader(), new Class[]{DeviceRepository.class},
                (proxy, method, args) -> "findByIdAndOwnerEmail".equals(method.getName()) && device.getId().equals(args[0]) && device.getOwnerEmail().equals(args[1]) ? Optional.of(device) : Optional.empty());
        AlertRepository alerts = (AlertRepository) Proxy.newProxyInstance(AlertRepository.class.getClassLoader(), new Class[]{AlertRepository.class},
                (proxy, method, args) -> "findByIdAndOwnerEmail".equals(method.getName()) && alert != null && alert.getId().equals(args[0]) && alert.getOwnerEmail().equals(args[1]) ? Optional.of(alert) : Optional.empty());
        return new MaintenanceTaskService(tasks, devices, alerts);
    }

    private MaintenanceTaskRequest request(String deviceId, String alertId, TaskStatus status) {
        MaintenanceTaskRequest request = new MaintenanceTaskRequest();
        request.setTitle("Inspect device"); request.setDescription("Check the device."); request.setDeviceId(deviceId); request.setAlertId(alertId); request.setPriority(TaskPriority.HIGH); request.setStatus(status); request.setDueDate("2026-10-14");
        return request;
    }
    private Device device(String id, String owner) { Device device = new Device(); device.setId(id); device.setOwnerEmail(owner); return device; }
    private Alert alert(String id, String owner, String deviceId) { Alert alert = new Alert(); alert.setId(id); alert.setOwnerEmail(owner); alert.setDeviceId(deviceId); return alert; }
}
