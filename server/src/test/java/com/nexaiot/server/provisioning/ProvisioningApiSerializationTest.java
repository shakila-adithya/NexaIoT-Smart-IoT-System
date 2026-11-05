package com.nexaiot.server.provisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.nexaiot.server.provisioning.dto.EnrollmentOperationResponse;
import com.nexaiot.server.provisioning.dto.PairingSessionResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;

class ProvisioningApiSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(
            new SimpleModule().addSerializer(Instant.class, ToStringSerializer.instance));

    @Test
    void safePairingSessionResponseDoesNotSerializeTokenMaterial() throws Exception {
        PairingSessionResponse response = new PairingSessionResponse(
                "session-1", "device-1", "ACTIVE", null, null, null, 0, 5, null);

        String json = objectMapper.writeValueAsString(response);

        assertFalse(json.contains("pairingToken"));
        assertFalse(json.contains("tokenHash"));
    }

    @Test
    void safeEnrollmentOperationResponseDoesNotSerializeBrokerOrCredentialMaterial() throws Exception {
        EnrollmentOperationResponse response = new EnrollmentOperationResponse(
                "operation-1", "device-1", "session-1", "attempt-1", "CREATED", 0,
                null, null,
                false, false, false, false, null, null, false, false);

        String json = objectMapper.writeValueAsString(response);

        assertFalse(json.contains("brokerUsername"));
        assertFalse(json.contains("brokerClientId"));
        assertFalse(json.contains("credentialReference"));
        assertFalse(json.contains("ownershipMarker"));
    }
}
