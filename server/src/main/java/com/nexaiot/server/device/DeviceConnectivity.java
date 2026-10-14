package com.nexaiot.server.device;

public class DeviceConnectivity {

    private ConnectivityType type;
    private ConnectivityProtocol protocol = ConnectivityProtocol.MQTT;

    public ConnectivityType getType() {
        return type;
    }

    public void setType(ConnectivityType type) {
        this.type = type;
    }

    public ConnectivityProtocol getProtocol() {
        return protocol;
    }

    public void setProtocol(ConnectivityProtocol protocol) {
        this.protocol = protocol;
    }
}
