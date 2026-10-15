package com.nexaiot.server.report.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
public class GenerateReportRequest {
    @NotBlank(message = "A device is required")
    private String deviceId;
    @NotBlank(message = "A report range is required")
    @Pattern(regexp = "1h|6h|24h|7d", message = "Unsupported report range. Use 1h, 6h, 24h, or 7d.")
    private String range;
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String value) { deviceId = value; }
    public String getRange() { return range; }
    public void setRange(String value) { range = value; }
}
