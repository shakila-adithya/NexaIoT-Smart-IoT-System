package com.nexaiot.server.device;

import java.util.LinkedHashSet;
import java.util.Set;

public class DeviceCapabilities {

    private Set<SensorCapability> sensors = new LinkedHashSet<>();
    private Set<DeviceMetricCapability> deviceMetrics = new LinkedHashSet<>();
    private Set<ControlCapability> controls = new LinkedHashSet<>();

    public DeviceCapabilities() {
    }

    public Set<SensorCapability> getSensors() {
        return sensors;
    }

    public void setSensors(Set<SensorCapability> sensors) {
        this.sensors = sensors;
    }

    public Set<DeviceMetricCapability> getDeviceMetrics() {
        return deviceMetrics;
    }

    public void setDeviceMetrics(Set<DeviceMetricCapability> deviceMetrics) {
        this.deviceMetrics = deviceMetrics;
    }

    public Set<ControlCapability> getControls() {
        return controls;
    }

    public void setControls(Set<ControlCapability> controls) {
        this.controls = controls;
    }
}
