package com.nexaiot.server.provisioning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEnrollmentOperationRequest(
        @NotBlank(message = "Pairing session ID is required.")
        @Size(max = 128, message = "Pairing session ID is too long.")
        String pairingSessionId
) {
}
