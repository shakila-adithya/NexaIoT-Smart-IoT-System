package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@EnabledIfSystemProperty(named = "nexaiot.integration.isolated-readonly", matches = "true")
class IsolatedMosquittoDynamicSecurityReadOnlyTest {

    private static final String DEFAULT_BROKER_URL = "ssl://localhost:8884";
    private static final String DEFAULT_CLIENT_ID = "nexaiot-provisioning-management";
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_CA = "C:/Users/SHAKILA/AppData/Local/NexaIoT/mqtt-lab/broker-certs/ca.crt";

    @Test
    void readsAdminRoleAndDefaultAclInventory() throws Exception {
        String brokerUrl = environment("MQTT_MANAGEMENT_BROKER_URL", DEFAULT_BROKER_URL);
        String clientId = environment("MQTT_MANAGEMENT_CLIENT_ID", DEFAULT_CLIENT_ID);
        String username = environment("MQTT_MANAGEMENT_USERNAME", DEFAULT_USERNAME);
        String password = requiredEnvironment("MQTT_MANAGEMENT_PASSWORD");
        String caCertificate = environment("MQTT_MANAGEMENT_CA_CERTIFICATE", DEFAULT_CA);
        assertTrue(Files.isRegularFile(Path.of(caCertificate)));

        ObjectMapper mapper = new ObjectMapper();
        MosquittoDynamicSecurityClient client = new MosquittoDynamicSecurityClient(
                mapper, true, brokerUrl, clientId, username, password, true, 5, caCertificate);

        BrokerClientInspection identity = client.inspectClient(username);
        assertEquals(username, identity.username());
        assertTrue(identity.acknowledged());
        assertFalse(identity.disabled());
        assertFalse(identity.clientIdPresent());
        assertNull(identity.clientId());

        BrokerRoleStatus role = client.inspectRole("dynsec-admin");
        assertEquals("dynsec-admin", role.roleName());
        assertTrue(role.referencesKnown());
        assertTrue(role.acknowledged());

        ObjectNode listClients = command(mapper, "listClients");
        listClients.put("verbose", true);
        listClients.put("count", -1);
        listClients.put("offset", 0);
        ObjectNode listRoles = command(mapper, "listRoles");
        listRoles.put("verbose", true);
        listRoles.put("count", -1);
        listRoles.put("offset", 0);
        ObjectNode defaults = command(mapper, "getDefaultACLAccess");

        List<JsonNode> responses = client.executeReadOnlyInventory(
                List.of(listClients, listRoles, defaults));
        assertEquals(3, responses.size());
        assertTrue(responses.get(0).path("data").path("clients").isArray());
        assertTrue(responses.get(1).path("data").path("roles").isArray());

        JsonNode aclEntries = responses.get(2).path("data").path("acls");
        assertEquals(4, aclEntries.size());
        Map<String, Boolean> expected = Map.of(
                "publishClientSend", false,
                "publishClientReceive", false,
                "subscribe", false,
                "unsubscribe", false);
        for (JsonNode entry : aclEntries) {
            String aclType = entry.path("acltype").asText();
            assertNotNull(expected.get(aclType));
            assertEquals(expected.get(aclType), entry.path("allow").asBoolean());
        }
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

    private String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
