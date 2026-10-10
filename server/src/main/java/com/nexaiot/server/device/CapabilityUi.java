package com.nexaiot.server.device;

public class CapabilityUi {

    private String icon;
    private Integer order;

    public CapabilityUi() {
    }

    public CapabilityUi(String icon, Integer order) {
        this.icon = icon;
        this.order = order;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }
}
