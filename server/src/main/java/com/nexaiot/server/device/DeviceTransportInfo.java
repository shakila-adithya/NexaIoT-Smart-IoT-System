package com.nexaiot.server.device;

/**
 * Application transport metadata. Physical connectivity remains represented
 * by the existing DeviceConnectivity field.
 */
public class DeviceTransportInfo {

    private DeviceTransportType transportType;
    private String protocol;
    private String mqttClientId;
    private String gatewayDeviceId;

    public DeviceTransportType getTransportType() {
        return transportType;
    }

    public void setTransportType(DeviceTransportType transportType) {
        this.transportType = transportType;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getMqttClientId() {
        return mqttClientId;
    }

    public void setMqttClientId(String mqttClientId) {
        this.mqttClientId = mqttClientId;
    }

    public String getGatewayDeviceId() {
        return gatewayDeviceId;
    }

    public void setGatewayDeviceId(String gatewayDeviceId) {
        this.gatewayDeviceId = gatewayDeviceId;
    }
}
