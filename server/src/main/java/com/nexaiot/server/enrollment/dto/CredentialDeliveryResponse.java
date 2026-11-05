package com.nexaiot.server.enrollment.dto;

public record CredentialDeliveryResponse(
        String operationId,
        String username,
        String mqttClientId,
        String password
) {
}
