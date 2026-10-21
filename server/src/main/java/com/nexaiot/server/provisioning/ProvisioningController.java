package com.nexaiot.server.provisioning;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import com.nexaiot.server.provisioning.dto.ClaimPairingSessionRequest;
import com.nexaiot.server.provisioning.dto.CreateEnrollmentOperationRequest;
import com.nexaiot.server.provisioning.dto.EnrollmentOperationResponse;
import com.nexaiot.server.provisioning.dto.PairingClaimResponse;
import com.nexaiot.server.provisioning.dto.PairingSessionResponse;
import com.nexaiot.server.provisioning.dto.PairingSessionStartResponse;
import com.nexaiot.server.provisioning.dto.ProvisioningMetadataResponse;

import jakarta.validation.Valid;
import com.nexaiot.server.security.RateLimitProperties;
import com.nexaiot.server.security.RateLimitService;

@RestController
@RequestMapping("/api")
public class ProvisioningController {

    private final ProvisioningApiService provisioningApiService;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;

    public ProvisioningController(
            ProvisioningApiService provisioningApiService,
            RateLimitService rateLimitService,
            RateLimitProperties rateLimitProperties
    ) {
        this.provisioningApiService = provisioningApiService;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
    }

    @PostMapping("/devices/{deviceId}/pairing-sessions")
    public ResponseEntity<PairingSessionStartResponse> startPairing(
            @PathVariable String deviceId,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                provisioningApiService.start(deviceId, authentication.getName()));
    }

    @GetMapping("/devices/{deviceId}/provisioning")
    public ProvisioningMetadataResponse metadata(
            @PathVariable String deviceId,
            Authentication authentication
    ) {
        return provisioningApiService.metadata(deviceId, authentication.getName());
    }

    @GetMapping("/devices/{deviceId}/pairing-sessions/{sessionId}")
    public PairingSessionResponse getPairingSession(
            @PathVariable String deviceId,
            @PathVariable String sessionId,
            Authentication authentication
    ) {
        return provisioningApiService.getSession(deviceId, sessionId, authentication.getName());
    }

    @DeleteMapping("/devices/{deviceId}/pairing-sessions/{sessionId}")
    public ResponseEntity<Void> cancelPairing(
            @PathVariable String deviceId,
            @PathVariable String sessionId,
            Authentication authentication
    ) {
        provisioningApiService.cancel(deviceId, sessionId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /**
     * The device-side proof endpoint. It is the only unauthenticated provisioning
     * endpoint and requires the high-entropy, one-time pairing token.
     */
    @PostMapping("/provisioning/pairing-sessions/{sessionId}/claim")
    public PairingClaimResponse claimPairing(
            @PathVariable String sessionId,
            @Valid @RequestBody ClaimPairingSessionRequest request,
            HttpServletRequest httpRequest
    ) {
        rateLimitService.requireAllowed(
                "pairing-claim-ip", httpRequest.getRemoteAddr(),
                rateLimitProperties.getPairingClaimMax(),
                rateLimitProperties.getPairingClaimWindowSeconds());
        rateLimitService.requireAllowed(
                "pairing-claim-session", sessionId,
                rateLimitProperties.getPairingClaimMax(),
                rateLimitProperties.getPairingClaimWindowSeconds());
        return provisioningApiService.claim(
                sessionId, request.pairingToken(), request.hardwareIdentityHash());
    }

    @PostMapping("/devices/{deviceId}/enrollment-operations")
    public ResponseEntity<EnrollmentOperationResponse> createEnrollmentOperation(
            @PathVariable String deviceId,
            @Valid @RequestBody CreateEnrollmentOperationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                provisioningApiService.createOperation(
                        deviceId, request.pairingSessionId(), authentication.getName()));
    }

    @GetMapping("/devices/{deviceId}/enrollment-operations/{operationId}")
    public EnrollmentOperationResponse getEnrollmentOperation(
            @PathVariable String deviceId,
            @PathVariable String operationId,
            Authentication authentication
    ) {
        return provisioningApiService.getOperation(
                deviceId, operationId, authentication.getName());
    }

    @PostMapping("/devices/{deviceId}/enrollment-operations/{operationId}/reconcile")
    public EnrollmentOperationResponse reconcileEnrollmentOperation(
            @PathVariable String deviceId,
            @PathVariable String operationId,
            Authentication authentication
    ) {
        return provisioningApiService.reconcileOperation(
                deviceId, operationId, authentication.getName());
    }
}
