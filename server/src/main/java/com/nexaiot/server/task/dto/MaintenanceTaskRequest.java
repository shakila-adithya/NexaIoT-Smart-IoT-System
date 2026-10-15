package com.nexaiot.server.task.dto;
import com.nexaiot.server.task.TaskPriority;
import com.nexaiot.server.task.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public class MaintenanceTaskRequest {
    @NotBlank(message = "Task title is required")
    @Size(max = 160, message = "Task title must be 160 characters or fewer")
    private String title;
    @Size(max = 2000, message = "Task description must be 2000 characters or fewer")
    private String description;
    @NotBlank(message = "A device is required")
    private String deviceId;
    private String alertId;
    private TaskPriority priority;
    private TaskStatus status;
    private String dueDate;
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
    public String getDueDate() { return dueDate; }
    public void setDueDate(String value) { dueDate = value; }
}
