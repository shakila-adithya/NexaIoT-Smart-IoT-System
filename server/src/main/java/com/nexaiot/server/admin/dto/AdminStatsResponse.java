package com.nexaiot.server.admin.dto;

public record AdminStatsResponse(long users, long devices, long onlineDevices, long offlineDevices, long maintenanceDevices, long otherDevices) {
}
