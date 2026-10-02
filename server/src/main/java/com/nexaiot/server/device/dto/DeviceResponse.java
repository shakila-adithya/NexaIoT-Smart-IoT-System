package com.nexaiot.server.device.dto;

import java.time.Instant;

public class DeviceResponse {

    private String id;
    private String deviceKey;
    private String name;
    private String type;
    private String location;
    private String status;
    private boolean powerOn;
    private Instant createdAt;
    private Instant updatedAt;

    public DeviceResponse() {
    }

    public DeviceResponse(
            String id,
            String deviceKey,
            String name,
            String type,
            String location,
            String status,
            boolean powerOn,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.deviceKey = deviceKey;
        this.name = name;
        this.type = type;
        this.location = location;
        this.status = status;
        this.powerOn = powerOn;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getDeviceKey() {
        return deviceKey;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getLocation() {
        return location;
    }

    public String getStatus() {
        return status;
    }

    public boolean isPowerOn() {
        return powerOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}