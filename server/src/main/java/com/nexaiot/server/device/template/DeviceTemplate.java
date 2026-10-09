package com.nexaiot.server.device.template;

import com.nexaiot.server.device.CapabilityManifest;

public class DeviceTemplate {

    private String templateId;
    private Integer version;
    private String name;
    private String category;
    private String description;
    private boolean enabled;
    private CapabilityManifest capabilityManifest;

    public DeviceTemplate() {
    }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public CapabilityManifest getCapabilityManifest() { return capabilityManifest; }
    public void setCapabilityManifest(CapabilityManifest capabilityManifest) { this.capabilityManifest = capabilityManifest; }
}
