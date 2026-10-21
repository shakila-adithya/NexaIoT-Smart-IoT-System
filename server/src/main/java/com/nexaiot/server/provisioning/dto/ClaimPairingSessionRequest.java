package com.nexaiot.server.provisioning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClaimPairingSessionRequest(
        @NotBlank(message = "Pairing token is required.")
        @Size(max = 256, message = "Pairing token is too long.")
        String pairingToken,

        @Size(max = 256, message = "Hardware identity is too long.")
        String hardwareIdentityHash
) {
}
