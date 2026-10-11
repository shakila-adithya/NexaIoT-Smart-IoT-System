package com.nexaiot.server.task;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "maintenance_tasks")
public class MaintenanceTask {
    @Id private String id;
    private String ownerEmail;
    private String title;
    private String description;
    private String deviceId;
    private String alertId;
    private TaskPriority priority;
    private TaskStatus status;
    private Instant dueDate;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String value) { ownerEmail = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String value) { deviceId = value; }
    public String getAlertId() { return alertId; }
    public void setAlertId(String value) { alertId = value; }
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority value) { priority = value; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus value) { status = value; }
    public Instant getDueDate() { return dueDate; }
    public void setDueDate(Instant value) { dueDate = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant value) { completedAt = value; }
}
