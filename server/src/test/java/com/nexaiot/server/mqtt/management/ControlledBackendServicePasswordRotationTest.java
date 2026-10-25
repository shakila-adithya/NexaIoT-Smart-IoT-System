package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@EnabledIfSystemProperty(named = "nexaiot.integration.backend-rotation", matches = "true")
class ControlledBackendServicePasswordRotationTest {

    private static final String TARGET = "ssl://localhost:8884";
    private static final String CONFIRMATION = "ROTATE_BACKEND_PASSWORD_ON_LOCALHOST_8884";

    @Test
    void rotatesOnlyTheVerifiedBackendIdentity() throws Exception {
        assertEquals(CONFIRMATION, System.getProperty("nexaiot.integration.backend-rotation-confirmation"));
        String brokerUrl = requiredEnvironment("MQTT_MANAGEMENT_BROKER_URL");
        String caCertificate = requiredEnvironment("MQTT_MANAGEMENT_CA_CERTIFICATE");
        String adminPassword = requiredEnvironment("MQTT_MANAGEMENT_PASSWORD");
        String replacementPassword = requiredEnvironment("MQTT_BACKEND_REPLACEMENT_PASSWORD");
        assertEquals(TARGET, brokerUrl);
        assertTrue(Files.isRegularFile(Path.of(caCertificate)));
        assertTrue(replacementPassword.length() >= 32);

        MosquittoDynamicSecurityClient broker = new MosquittoDynamicSecurityClient(
                new ObjectMapper(), true, brokerUrl, "nexaiot-provisioning-management", "admin",
                adminPassword, true, 5, caCertificate);
        verifyPreflight(broker);
        broker.rotateClientPassword(BackendMqttServicePolicy.USERNAME, replacementPassword);
        verifyEffectivePermissions(brokerUrl, caCertificate, replacementPassword);
    }

    private void verifyPreflight(MosquittoDynamicSecurityClient broker) {
        BrokerClientInspection client = broker.inspectClient(BackendMqttServicePolicy.USERNAME);
        assertEquals(BackendMqttServicePolicy.USERNAME, client.username());
        assertEquals(BackendMqttServicePolicy.CLIENT_ID, client.clientId());
        assertTrue(client.clientIdPresent());
        assertFalse(client.disabled());

        BrokerRoleStatus roleStatus = broker.inspectRole(BackendMqttServicePolicy.ROLE_NAME);
        assertEquals(List.of(BackendMqttServicePolicy.USERNAME), roleStatus.referencedClients());
        assertTrue(roleStatus.referencedGroups().isEmpty());

        JsonNode role = broker.inspectRolePermissions(BackendMqttServicePolicy.ROLE_NAME);
        Set<String> actual = new HashSet<>();
        for (JsonNode acl : role.path("acls")) {
            actual.add(acl.path("acltype").asText() + "|"
                    + acl.path("topic").asText() + "|"
                    + acl.path("allow").asBoolean() + "|"
                    + acl.path("priority").asInt());
        }
        Set<String> expected = new HashSet<>();
        for (ObjectNode command : BackendMqttServicePolicy.roleCommands(new ObjectMapper())) {
            if ("addRoleACL".equals(command.path("command").asText())) {
                expected.add(command.path("acltype").asText() + "|"
                        + command.path("topic").asText() + "|true|100");
            }
        }
        assertEquals(expected, actual);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode listClients = command(mapper, "listClients");
        listClients.put("verbose", true);
        listClients.put("count", -1);
        listClients.put("offset", 0);
        ObjectNode listGroups = command(mapper, "listGroups");
        listGroups.put("verbose", true);
        listGroups.put("count", -1);
        listGroups.put("offset", 0);
        List<JsonNode> inventory = broker.executeReadOnlyInventory(List.of(listClients, listGroups));
        JsonNode backend = find(inventory.get(0).path("data").path("clients"), "username",
                BackendMqttServicePolicy.USERNAME);
        assertNotNull(backend);
        assertEquals(1, backend.path("roles").size());
        assertEquals(BackendMqttServicePolicy.ROLE_NAME, backend.path("roles").get(0).path("rolename").asText());
        assertEquals(100, backend.path("roles").get(0).path("priority").asInt());
        assertTrue(!backend.has("groups") || backend.path("groups").isEmpty());
        assertTrue(roleStatus.referencedGroups().isEmpty());
    }

    private void verifyEffectivePermissions(String brokerUrl, String caCertificate, String password)
            throws Exception {
        MqttAsyncClient client = new MqttAsyncClient(brokerUrl, BackendMqttServicePolicy.CLIENT_ID);
        try {
            client.connect(options(caCertificate, password)).waitForCompletion(5000);
            assertSubscription(client, BackendMqttServicePolicy.TELEMETRY_TOPIC);
            assertSubscription(client, BackendMqttServicePolicy.STATE_TOPIC);
            assertSubscription(client, BackendMqttServicePolicy.COMMAND_RESULT_TOPIC);
            assertThrows(MqttException.class, () -> client.subscribe(
                    "$CONTROL/dynamic-security/v1", 1).waitForCompletion(5000));
        } finally {
            if (client.isConnected()) {
                client.disconnect().waitForCompletion(5000);
            }
            client.close();
        }
    }

    private void assertSubscription(MqttAsyncClient client, String topic) throws Exception {
        var token = client.subscribe(topic, 1);
        token.waitForCompletion(5000);
        int[] granted = token.getGrantedQos();
        assertEquals(1, granted.length);
        assertTrue(granted[0] >= 0);
    }

    private MqttConnectOptions options(String caCertificate, String password) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(BackendMqttServicePolicy.USERNAME);
        options.setPassword(password.toCharArray());
        options.setSocketFactory(MqttManagementTls.socketFactory(caCertificate));
        options.setHttpsHostnameVerificationEnabled(true);
        options.setAutomaticReconnect(false);
        options.setCleanSession(true);
        options.setConnectionTimeout(5);
        return options;
    }

    private ObjectNode command(ObjectMapper mapper, String name) {
        ObjectNode command = mapper.createObjectNode();
        command.put("command", name);
        return command;
    }

    private JsonNode find(JsonNode entries, String field, String value) {
        for (JsonNode entry : entries) {
            if (value.equals(entry.path(field).asText())) {
                return entry;
            }
        }
        return null;
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for backend password rotation.");
        }
        return value;
    }
}
