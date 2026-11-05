package com.nexaiot.server.firmware.dto;

import java.time.Instant;
import java.util.List;

public record FirmwarePackageResponse(String id, String version, List<String> compatibleDeviceTypes, String originalFilename, String contentType, long size, String sha256, Instant uploadedAt, String uploadedBy) {
}
