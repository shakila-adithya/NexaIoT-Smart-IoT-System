package com.nexaiot.server.enrollment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CredentialDeliveryRequest(
        @NotBlank @Size(max = 512) String mqttClientId,
        @NotBlank @Size(max = 512) String challenge,
        @NotBlank @Size(max = 4096) String signature
) {
}
