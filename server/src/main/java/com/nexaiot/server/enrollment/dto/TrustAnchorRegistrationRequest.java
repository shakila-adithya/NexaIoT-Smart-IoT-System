package com.nexaiot.server.enrollment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrustAnchorRegistrationRequest(
        @NotBlank @Size(max = 4096) String mqttClientId,
        @NotBlank @Size(max = 4096) String publicKey,
        @NotBlank @Size(max = 4096) String signature
) {
}
