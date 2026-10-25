package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

class MosquittoDynamicSecurityPasswordRotationTest {

    @Test
    void buildsSetClientPasswordCommandWithExactFields() {
        JsonNode command = MosquittoDynamicSecurityClient.setClientPasswordCommand(
                new ObjectMapper(), "nexaiot-backend-lab", "replacement-password");

        assertEquals("setClientPassword", command.path("command").asText());
        assertEquals("nexaiot-backend-lab", command.path("username").asText());
        assertEquals("replacement-password", command.path("password").asText());
        assertEquals(3, command.size());
    }

    @Test
    void rejectsBlankRotationInputsBeforeBrokerAccess() {
        MosquittoDynamicSecurityClient client = new MosquittoDynamicSecurityClient(
                new ObjectMapper(), false, "ssl://localhost:8884", "test", "admin",
                "unused", true, 5, "unused");

        assertThrows(BrokerManagementException.class,
                () -> client.rotateClientPassword("nexaiot-backend-lab", "replacement"));
    }
}
