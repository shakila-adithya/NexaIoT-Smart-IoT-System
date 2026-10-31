package com.nexaiot.server.device;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "devices")
public class Device {

    @Id
    private String id;

    @Indexed(unique = true)
    private String deviceKey;

    @Indexed
    private String ownerEmail;
    private String ownerUserId;

    private String name;
    private String type;
    private String location;
    private String status;
    private boolean powerOn;
    private Map<String, Object> reportedStates;
    private DeviceConnectivity connectivity;
    private DeviceCapabilities capabilities;
    private CapabilityManifest capabilityManifest;
    private DeviceHardwareIdentity hardwareIdentity;
    private DeviceTransportInfo transportInfo;
    private DeviceProvisioningInfo provisioningInfo;
    private DeviceRuntimeInfo runtimeInfo;
    private DeviceMqttCredentialInfo mqttCredentialInfo;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastSeenAt;

    public Device() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDeviceKey() {
        return deviceKey;
    }

    public void setDeviceKey(String deviceKey) {
        this.deviceKey = deviceKey;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isPowerOn() {
        return powerOn;
    }

    public void setPowerOn(boolean powerOn) {
        this.powerOn = powerOn;
    }

    public Map<String, Object> getReportedStates() {
        return reportedStates;
    }

    public void setReportedStates(Map<String, Object> reportedStates) {
        this.reportedStates = reportedStates;
    }

    public DeviceConnectivity getConnectivity() {
        return connectivity;
    }

    public void setConnectivity(DeviceConnectivity connectivity) {
        this.connectivity = connectivity;
    }

    public DeviceCapabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(DeviceCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    public CapabilityManifest getCapabilityManifest() {
        return capabilityManifest;
    }

    public void setCapabilityManifest(CapabilityManifest capabilityManifest) {
        this.capabilityManifest = capabilityManifest;
    }

    public DeviceHardwareIdentity getHardwareIdentity() {
        return hardwareIdentity;
    }

    public void setHardwareIdentity(DeviceHardwareIdentity hardwareIdentity) {
        this.hardwareIdentity = hardwareIdentity;
    }

    public DeviceTransportInfo getTransportInfo() {
        return transportInfo;
    }

    public void setTransportInfo(DeviceTransportInfo transportInfo) {
        this.transportInfo = transportInfo;
    }

    public DeviceProvisioningInfo getProvisioningInfo() {
        return provisioningInfo;
    }

    public void setProvisioningInfo(DeviceProvisioningInfo provisioningInfo) {
        this.provisioningInfo = provisioningInfo;
    }

    public DeviceRuntimeInfo getRuntimeInfo() {
        return runtimeInfo;
    }

    public void setRuntimeInfo(DeviceRuntimeInfo runtimeInfo) {
        this.runtimeInfo = runtimeInfo;
    }

    public DeviceMqttCredentialInfo getMqttCredentialInfo() {
        return mqttCredentialInfo;
    }

    public void setMqttCredentialInfo(DeviceMqttCredentialInfo mqttCredentialInfo) {
        this.mqttCredentialInfo = mqttCredentialInfo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
