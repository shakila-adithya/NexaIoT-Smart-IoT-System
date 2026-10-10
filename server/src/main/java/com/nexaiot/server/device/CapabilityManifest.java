package com.nexaiot.server.device;

import java.util.ArrayList;
import java.util.List;

public class CapabilityManifest {

    public static final int SUPPORTED_VERSION = 1;

    private Integer version = SUPPORTED_VERSION;
    private CapabilityManifestSource source;
    private String templateId;
    private List<CapabilityDefinition> capabilities = new ArrayList<>();

    public CapabilityManifest() {
    }

    public CapabilityManifest(CapabilityManifestSource source) {
        this.source = source;
    }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public CapabilityManifestSource getSource() { return source; }
    public void setSource(CapabilityManifestSource source) { this.source = source; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public List<CapabilityDefinition> getCapabilities() { return capabilities; }
    public void setCapabilities(List<CapabilityDefinition> capabilities) { this.capabilities = capabilities; }
}
