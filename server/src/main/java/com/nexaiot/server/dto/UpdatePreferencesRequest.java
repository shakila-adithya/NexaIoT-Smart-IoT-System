package com.nexaiot.server.dto;

public class UpdatePreferencesRequest {

    private String temperatureUnit;
    private String dateFormat;
    private Boolean compactMode;
    private Boolean liveChartAnimations;
    private Boolean dashboardAutoRefresh;

    public String getTemperatureUnit() { return temperatureUnit; }
    public String getDateFormat() { return dateFormat; }
    public Boolean getCompactMode() { return compactMode; }
    public Boolean getLiveChartAnimations() { return liveChartAnimations; }
    public Boolean getDashboardAutoRefresh() { return dashboardAutoRefresh; }

    public void setTemperatureUnit(String temperatureUnit) { this.temperatureUnit = temperatureUnit; }
    public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }
    public void setCompactMode(Boolean compactMode) { this.compactMode = compactMode; }
    public void setLiveChartAnimations(Boolean liveChartAnimations) { this.liveChartAnimations = liveChartAnimations; }
    public void setDashboardAutoRefresh(Boolean dashboardAutoRefresh) { this.dashboardAutoRefresh = dashboardAutoRefresh; }
}
