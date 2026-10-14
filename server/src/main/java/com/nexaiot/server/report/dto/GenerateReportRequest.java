package com.nexaiot.server.report.dto;
public class GenerateReportRequest {
    private String deviceId;
    private String range;
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String value) { deviceId = value; }
    public String getRange() { return range; }
    public void setRange(String value) { range = value; }
}
