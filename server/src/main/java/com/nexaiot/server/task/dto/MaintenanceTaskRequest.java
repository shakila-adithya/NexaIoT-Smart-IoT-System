package com.nexaiot.server.task.dto;
import com.nexaiot.server.task.TaskPriority;
import com.nexaiot.server.task.TaskStatus;
public class MaintenanceTaskRequest {
    private String title;
    private String description;
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
