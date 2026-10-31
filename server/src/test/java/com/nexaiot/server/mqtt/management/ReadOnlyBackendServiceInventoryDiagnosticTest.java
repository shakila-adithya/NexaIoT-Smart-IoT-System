package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@EnabledIfSystemProperty(named = "nexaiot.integration.backend-diagnostic", matches = "true")
class ReadOnlyBackendServiceInventoryDiagnosticTest {

    private static final String TARGET = "ssl://localhost:8884";

    @Test
    void reportsBackendClientAndRoleWithoutMutation() throws Exception {
        String brokerUrl = requiredEnvironment("MQTT_MANAGEMENT_BROKER_URL");
        String caCertificate = requiredEnvironment("MQTT_MANAGEMENT_CA_CERTIFICATE");
        String managementPassword = requiredEnvironment("MQTT_MANAGEMENT_PASSWORD");
        assertEquals(TARGET, brokerUrl);
        assertTrue(Files.isRegularFile(Path.of(caCertificate)));

        ObjectMapper mapper = new ObjectMapper();
        MosquittoDynamicSecurityClient broker = new MosquittoDynamicSecurityClient(
                mapper, true, brokerUrl, "nexaiot-provisioning-management",
                "admin", managementPassword, true, 5, caCertificate);

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
        List<JsonNode> responses = broker.executeReadOnlyInventory(List.of(clients, roles, groups));

        JsonNode clientEntries = responses.get(0).path("data").path("clients");
        JsonNode roleEntries = responses.get(1).path("data").path("roles");
        JsonNode groupEntries = responses.get(2).path("data").path("groups");
        List<String> clientNames = names(clientEntries, "username");
        List<String> roleNames = names(roleEntries, "rolename");
        System.out.println("BACKEND_DIAGNOSTIC_CLIENTS=" + clientNames);
        System.out.println("BACKEND_DIAGNOSTIC_ROLES=" + roleNames);

        JsonNode backendEntry = find(clientEntries, "username", BackendMqttServicePolicy.USERNAME);
        System.out.println("BACKEND_DIAGNOSTIC_CLIENT_ROLES=" + roles(backendEntry));
        System.out.println("BACKEND_DIAGNOSTIC_CLIENT_GROUPS=" + clientGroups(backendEntry));
        System.out.println("BACKEND_DIAGNOSTIC_ADMIN_ROLES=" + roles(find(clientEntries, "username", "admin")));
        System.out.println("BACKEND_DIAGNOSTIC_BACKEND_ROLE_CLIENTS=" + roleOwners(clientEntries, BackendMqttServicePolicy.ROLE_NAME, "username"));
        System.out.println("BACKEND_DIAGNOSTIC_BACKEND_ROLE_GROUPS=" + roleOwners(groupEntries, BackendMqttServicePolicy.ROLE_NAME, "groupname"));
        System.out.println("BACKEND_DIAGNOSTIC_GROUPS=" + names(groupEntries, "groupname"));

        if (clientNames.contains(BackendMqttServicePolicy.USERNAME)) {
            BrokerClientInspection client = broker.inspectClient(BackendMqttServicePolicy.USERNAME);
            System.out.println("BACKEND_DIAGNOSTIC_CLIENT_ID=" + client.clientId());
            System.out.println("BACKEND_DIAGNOSTIC_CLIENT_ID_PRESENT=" + client.clientIdPresent());
            System.out.println("BACKEND_DIAGNOSTIC_CLIENT_DISABLED=" + client.disabled());
        } else {
            System.out.println("BACKEND_DIAGNOSTIC_CLIENT_ID=<absent>");
        }

        if (roleNames.contains(BackendMqttServicePolicy.ROLE_NAME)) {
            JsonNode role = broker.inspectRolePermissions(BackendMqttServicePolicy.ROLE_NAME);
            System.out.println("BACKEND_DIAGNOSTIC_ROLE_ACLS=" + role.path("acls"));
        } else {
            System.out.println("BACKEND_DIAGNOSTIC_ROLE_ACLS=<absent>");
        }
    }

    private List<String> names(JsonNode entries, String field) {
        List<String> names = new ArrayList<>();
        for (JsonNode entry : entries) {
            names.add(entry.path(field).asText());
        }
        return names;
    }

    private JsonNode find(JsonNode entries, String field, String value) {
        for (JsonNode entry : entries) {
            if (value.equals(entry.path(field).asText())) {
                return entry;
            }
        }
        return null;
    }

    private List<String> roles(JsonNode client) {
        if (client == null || !client.isObject()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode role : client.path("roles")) {
            String name = role.path("rolename").asText();
            String priority = role.has("priority") ? String.valueOf(role.path("priority").asInt()) : "<default>";
            values.add(name + "@" + priority);
        }
        return values;
    }

    private List<String> clientGroups(JsonNode client) {
        if (client == null || !client.isObject()) {
            return List.of();
        }
        return textNames(client.path("groups"));
    }

    private List<String> textNames(JsonNode entries) {
        List<String> values = new ArrayList<>();
        if (!entries.isArray()) {
            return values;
        }
        for (JsonNode entry : entries) {
            values.add(entry.asText());
        }
        return values;
    }

    private List<String> roleOwners(JsonNode entries, String roleName, String ownerField) {
        List<String> owners = new ArrayList<>();
        for (JsonNode entry : entries) {
            for (JsonNode role : entry.path("roles")) {
                if (roleName.equals(role.path("rolename").asText())) {
                    owners.add(entry.path(ownerField).asText());
                }
            }
        }
        return owners;
    }

    private ObjectNode command(ObjectMapper mapper, String name) {
        ObjectNode command = mapper.createObjectNode();
        command.put("command", name);
        return command;
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for read-only diagnosis.");
        }
        return value;
    }
}
