package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Authenticated broker inventory reconciliation. This test is opt-in and
 * contains only Dynamic Security read commands.
 */
@EnabledIfSystemProperty(named = "nexaiot.integration.reconciliation", matches = "true")
class ReadOnlyMosquittoBrokerReconciliationTest {

    private static final String BROKER_URL = "ssl://localhost:1885";
    private static final String MANAGEMENT_USERNAME = "nexaiot-provisioning-management";
    private static final String MANAGEMENT_CLIENT_ID = "nexaiot-provisioning-management";

    @Test
    void reconcilesBrokerInventoryWithoutMutation() {
        String password = requiredEnvironment("NEXAIOT_MQTT_MANAGEMENT_PASSWORD");
        String caCertificate = requiredEnvironment("NEXAIOT_MQTT_CA_CERTIFICATE");
        if (!Files.isRegularFile(Path.of(caCertificate))) {
            throw new IllegalStateException("Configured CA certificate is not readable.");
        }

        ObjectMapper mapper = new ObjectMapper();
        MosquittoDynamicSecurityClient broker = new MosquittoDynamicSecurityClient(
                mapper, true, BROKER_URL, MANAGEMENT_CLIENT_ID,
                MANAGEMENT_USERNAME, password, true, 5, caCertificate);

        ObjectNode clients = command(mapper, "listClients");
        clients.put("verbose", true);
        clients.put("count", -1);
        clients.put("offset", 0);
        ObjectNode roles = command(mapper, "listRoles");
        roles.put("verbose", true);
        roles.put("count", -1);
        roles.put("offset", 0);
        ObjectNode groups = command(mapper, "listGroups");
        groups.put("verbose", true);
        groups.put("count", -1);
        groups.put("offset", 0);
        ObjectNode acl = command(mapper, "getDefaultACLAccess");

        List<JsonNode> responses = broker.executeReadOnlyInventory(
                List.of(clients, roles, groups, acl));
        assertDoesNotThrow(() -> BrokerReconciliationVerifier.verify(
                responses.get(0).path("data"),
                responses.get(1).path("data"),
                responses.get(2).path("data"),
                responses.get(3).path("data")));
    }

    private ObjectNode command(ObjectMapper mapper, String name) {
        ObjectNode command = mapper.createObjectNode();
        command.put("command", name);
        return command;
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for this read-only test.");
        }
        return value;
    }
}
