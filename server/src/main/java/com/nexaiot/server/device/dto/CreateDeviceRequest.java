package com.nexaiot.server.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.nexaiot.server.device.DeviceCapabilities;

public class CreateDeviceRequest {

    @NotBlank(message = "Device name is required")
    @Size(max = 100, message = "Device name must be at most 100 characters")
    private String name;

    @NotBlank(message = "Device type is required")
    @Size(max = 100, message = "Device type must be at most 100 characters")
    private String type;

    @NotBlank(message = "Device location is required")
    @Size(max = 150, message = "Device location must be at most 150 characters")
    private String location;

    private DeviceCapabilities capabilities;

    public CreateDeviceRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public DeviceCapabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(DeviceCapabilities capabilities) {
        this.capabilities = capabilities;
    }
}
