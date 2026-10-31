package com.nexaiot.server.device;

/**
 * Optional physical hardware metadata for a logical device.
 *
 * The identity hash is metadata for a future provisioning flow; it is not
 * treated as proof of physical possession in the current registration flow.
 */
public class DeviceHardwareIdentity {

    private String family;
    private String manufacturer;
    private String model;
    private String hardwareRevision;
    private String hardwareIdentityHash;

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getHardwareRevision() {
        return hardwareRevision;
    }

    public void setHardwareRevision(String hardwareRevision) {
        this.hardwareRevision = hardwareRevision;
    }

    public String getHardwareIdentityHash() {
        return hardwareIdentityHash;
    }

    public void setHardwareIdentityHash(String hardwareIdentityHash) {
        this.hardwareIdentityHash = hardwareIdentityHash;
    }
}
