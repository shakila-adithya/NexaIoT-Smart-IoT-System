package com.nexaiot.server.device;

public class CapabilityControl {

    private CapabilityControlType type;

    public CapabilityControl() {
    }

    public CapabilityControl(CapabilityControlType type) {
        this.type = type;
    }

    public CapabilityControlType getType() {
        return type;
    }

    public void setType(CapabilityControlType type) {
        this.type = type;
    }
}
