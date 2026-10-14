package com.nexaiot.server.task;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.stereotype.Service;
import com.nexaiot.server.alert.Alert;
import com.nexaiot.server.alert.AlertNotFoundException;
import com.nexaiot.server.alert.AlertRepository;
import com.nexaiot.server.device.Device;
import com.nexaiot.server.device.DeviceNotFoundException;
import com.nexaiot.server.device.DeviceRepository;
import com.nexaiot.server.task.dto.MaintenanceTaskRequest;
import com.nexaiot.server.task.dto.MaintenanceTaskResponse;

@Service
public class MaintenanceTaskService {
    private final MaintenanceTaskRepository taskRepository;
    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;

    public MaintenanceTaskService(MaintenanceTaskRepository taskRepository, DeviceRepository deviceRepository, AlertRepository alertRepository) {
        this.taskRepository = taskRepository;
        this.deviceRepository = deviceRepository;
        this.alertRepository = alertRepository;
    }
    public List<MaintenanceTaskResponse> list(String owner) { return taskRepository.findByOwnerEmailOrderByCreatedAtDesc(normalize(owner)).stream().map(this::response).toList(); }
    public MaintenanceTaskResponse get(String id, String owner) { return response(findOwned(id, owner)); }
    public MaintenanceTaskResponse create(MaintenanceTaskRequest request, String ownerEmail) {
        String owner = normalize(ownerEmail);
        Device device = findDevice(request.getDeviceId(), owner);
        Alert alert = findAlert(request.getAlertId(), owner);
        validateLink(device, alert);
        MaintenanceTask task = new MaintenanceTask();
        task.setOwnerEmail(owner);
        task.setCreatedAt(Instant.now());
        apply(task, request, true);
        task.setDeviceId(device.getId());
        task.setAlertId(alert == null ? null : alert.getId());
        return response(taskRepository.save(task));
    }
    public MaintenanceTaskResponse update(String id, MaintenanceTaskRequest request, String ownerEmail) {
        MaintenanceTask task = findOwned(id, ownerEmail);
        if (request.getDeviceId() != null && !request.getDeviceId().equals(task.getDeviceId())) throw new IllegalArgumentException("The task device cannot be changed.");
        if (request.getAlertId() != null && !request.getAlertId().equals(task.getAlertId())) throw new IllegalArgumentException("The linked alert cannot be changed.");
        apply(task, request, false);
        return response(taskRepository.save(task));
    }
    public void delete(String id, String owner) { taskRepository.delete(findOwned(id, owner)); }

    private void apply(MaintenanceTask task, MaintenanceTaskRequest request, boolean creating) {
        if (request.getTitle() == null || request.getTitle().isBlank() || request.getTitle().trim().length() > 160) throw new IllegalArgumentException("Task title is required and must be 160 characters or fewer.");
        if (request.getDescription() != null && request.getDescription().length() > 2000) throw new IllegalArgumentException("Task description must be 2000 characters or fewer.");
        if (request.getPriority() == null) throw new IllegalArgumentException("Task priority is required.");
        TaskStatus next = request.getStatus() == null ? (creating ? TaskStatus.OPEN : task.getStatus()) : request.getStatus();
        if (creating && next != TaskStatus.OPEN) throw new IllegalArgumentException("New tasks must start as OPEN.");
        if (!creating && !allowed(task.getStatus(), next)) throw new IllegalArgumentException("Invalid task status transition.");
        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
        task.setPriority(request.getPriority());
        task.setStatus(next);
        task.setDueDate(parseDate(request.getDueDate()));
        task.setUpdatedAt(Instant.now());
        task.setCompletedAt(next == TaskStatus.COMPLETED ? (task.getCompletedAt() == null ? Instant.now() : task.getCompletedAt()) : null);
    }
    private boolean allowed(TaskStatus current, TaskStatus next) {
        if (current == next) return true;
        return (current == TaskStatus.OPEN && (next == TaskStatus.IN_PROGRESS || next == TaskStatus.CANCELLED))
                || (current == TaskStatus.IN_PROGRESS && (next == TaskStatus.COMPLETED || next == TaskStatus.CANCELLED));
    }
    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return value.length() == 10 ? LocalDate.parse(value).atTime(23, 59, 59).toInstant(ZoneOffset.UTC) : Instant.parse(value); }
        catch (DateTimeParseException ex) { throw new IllegalArgumentException("Due date must be a valid date."); }
    }
    private Device findDevice(String id, String owner) { if (id == null || id.isBlank()) throw new IllegalArgumentException("A device is required."); return deviceRepository.findByIdAndOwnerEmail(id, owner).orElseThrow(() -> new DeviceNotFoundException("Device not found")); }
    private Alert findAlert(String id, String owner) { return id == null || id.isBlank() ? null : alertRepository.findByIdAndOwnerEmail(id, normalize(owner)).orElseThrow(() -> new AlertNotFoundException("Alert not found")); }
    private void validateLink(Device device, Alert alert) { if (alert != null && !device.getId().equals(alert.getDeviceId())) throw new IllegalArgumentException("The alert does not belong to the selected device."); }
    private MaintenanceTask findOwned(String id, String owner) { return taskRepository.findByIdAndOwnerEmail(id, normalize(owner)).orElseThrow(() -> new MaintenanceTaskNotFoundException("Task not found")); }
    private MaintenanceTaskResponse response(MaintenanceTask task) { return new MaintenanceTaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getDeviceId(), task.getAlertId(), task.getPriority(), task.getStatus(), task.getDueDate(), task.getCreatedAt(), task.getUpdatedAt(), task.getCompletedAt()); }
    private String normalize(String value) { return value.trim().toLowerCase(); }
}
