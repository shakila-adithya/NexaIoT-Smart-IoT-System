package com.nexaiot.server.task.dto;
import java.time.Instant;
import com.nexaiot.server.task.TaskPriority;
import com.nexaiot.server.task.TaskStatus;
public record MaintenanceTaskResponse(String id, String title, String description, String deviceId, String alertId, TaskPriority priority, TaskStatus status, Instant dueDate, Instant createdAt, Instant updatedAt, Instant completedAt) { }
