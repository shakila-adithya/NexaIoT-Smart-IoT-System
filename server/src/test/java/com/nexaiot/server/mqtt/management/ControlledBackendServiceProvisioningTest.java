package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@EnabledIfSystemProperty(named = "nexaiot.integration.backend-provisioning", matches = "true")
class ControlledBackendServiceProvisioningTest {

    private static final String TARGET = "ssl://localhost:8884";
    private static final String MANAGEMENT_USERNAME = "admin";
    private static final String MANAGEMENT_CLIENT_ID = "nexaiot-provisioning-management";

    @Test
    void provisionsAndVerifiesOnlyTheDedicatedBackendIdentity() throws Exception {
        requireMutationAuthorization();
        String brokerUrl = requiredEnvironment("MQTT_MANAGEMENT_BROKER_URL");
        assertEquals(TARGET, brokerUrl);
        String caCertificate = requiredEnvironment("MQTT_MANAGEMENT_CA_CERTIFICATE");
        assertTrue(Files.isRegularFile(Path.of(caCertificate)));
        String managementPassword = requiredEnvironment("MQTT_MANAGEMENT_PASSWORD");
        String backendPassword = requiredEnvironment("MQTT_BACKEND_PASSWORD");
        String backendClientId = requiredEnvironment("MQTT_BACKEND_CLIENT_ID");
        assertEquals(BackendMqttServicePolicy.CLIENT_ID, backendClientId);

        MosquittoDynamicSecurityClient broker = new MosquittoDynamicSecurityClient(
                new ObjectMapper(), true, brokerUrl, MANAGEMENT_CLIENT_ID,
                MANAGEMENT_USERNAME, managementPassword, true, 5, caCertificate);

        verifyAdministrator(broker);
        assertTargetAbsent(broker, BackendMqttServicePolicy.USERNAME,
                BackendMqttServicePolicy.ROLE_NAME);

        boolean roleCreated = false;
        boolean clientCreated = false;
        try {
            broker.createBackendServiceRolePermissions();
            roleCreated = true;
            broker.createBackendServiceIdentity(backendClientId, backendPassword);
            clientCreated = true;
            broker.assignBackendServiceRole();
            verifyProvisionedInventory(broker, backendClientId);
            verifyEffectivePermissions(brokerUrl, backendClientId, backendPassword, caCertificate);
        } catch (RuntimeException exception) {
            try {
                rollback(broker, clientCreated, roleCreated);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    private void verifyAdministrator(MosquittoDynamicSecurityClient broker) {
        BrokerClientInspection admin = broker.inspectClient(MANAGEMENT_USERNAME);
        assertEquals(MANAGEMENT_USERNAME, admin.username());
        assertTrue(admin.acknowledged());
        assertEquals("dynsec-admin", broker.inspectRole("dynsec-admin").roleName());
    }

    private void assertTargetAbsent(
            MosquittoDynamicSecurityClient broker,
            String username,
            String roleName
    ) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode clients = command(mapper, "listClients");
        clients.put("verbose", true);
        clients.put("count", -1);
        clients.put("offset", 0);
        ObjectNode roles = command(mapper, "listRoles");
        roles.put("verbose", true);
        roles.put("count", -1);
        roles.put("offset", 0);
        List<JsonNode> responses = broker.executeReadOnlyInventory(List.of(clients, roles));
        assertFalse(containsName(responses.get(0).path("data").path("clients"), "username", username));
        assertFalse(containsName(responses.get(1).path("data").path("roles"), "rolename", roleName));
    }

    private void verifyProvisionedInventory(
            MosquittoDynamicSecurityClient broker,
            String backendClientId
    ) {
        BrokerClientInspection backend = broker.inspectClient(BackendMqttServicePolicy.USERNAME);
        assertEquals(backendClientId, backend.clientId());
        assertTrue(backend.clientIdPresent());
        assertEquals(BackendMqttServicePolicy.ROLE_NAME,
                broker.inspectRole(BackendMqttServicePolicy.ROLE_NAME).roleName());

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
    }

    private void verifyEffectivePermissions(
            String brokerUrl,
            String clientId,
            String password,
            String caCertificate
    ) throws Exception {
        MqttAsyncClient client = new MqttAsyncClient(brokerUrl, clientId);
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(BackendMqttServicePolicy.USERNAME);
            options.setPassword(password.toCharArray());
            options.setSocketFactory(MqttManagementTls.socketFactory(caCertificate));
            options.setHttpsHostnameVerificationEnabled(true);
            options.setAutomaticReconnect(false);
            options.setCleanSession(true);
            options.setConnectionTimeout(5);
            client.connect(options).waitForCompletion(5000);
            assertSubscription(client, BackendMqttServicePolicy.TELEMETRY_TOPIC);
            assertSubscription(client, BackendMqttServicePolicy.STATE_TOPIC);
            assertSubscription(client, BackendMqttServicePolicy.COMMAND_RESULT_TOPIC);
            client.publish(BackendMqttServicePolicy.COMMAND_TOPIC.replace("+", "NEXA-BACKEND-TEST"),
                    new byte[]{'{', '}'}, 1, false).waitForCompletion(5000);
            assertPublishNotDelivered(client, "nexaiot/devices/NEXA-BACKEND-TEST/telemetry");
            assertSubscribeDenied(client, "nexaiot/devices/NEXA-BACKEND-TEST/commands");
            assertSubscribeDenied(client, "$CONTROL/dynamic-security/v1");
        } finally {
            if (client.isConnected()) {
                client.disconnect().waitForCompletion(5000);
            }
            client.close();
        }
    }

    private void assertSubscription(MqttAsyncClient client, String topic) throws Exception {
        IMqttToken token = client.subscribe(topic, 1);
        token.waitForCompletion(5000);
        int[] granted = token.getGrantedQos();
        assertEquals(1, granted.length);
        assertTrue(granted[0] >= 0 && granted[0] <= 1);
    }

    private void assertPublishNotDelivered(MqttAsyncClient client, String topic) throws Exception {
        CountDownLatch delivery = new CountDownLatch(1);
        IMqttToken subscription = client.subscribe(topic, 1,
                (receivedTopic, message) -> delivery.countDown());
        subscription.waitForCompletion(5000);
        client.publish(topic, new byte[]{'{', '}'}, 1, false).waitForCompletion(5000);
        assertTrue(!delivery.await(2, TimeUnit.SECONDS));
    }

    private void assertSubscribeDenied(MqttAsyncClient client, String topic) {
        try {
            IMqttToken token = client.subscribe(topic, 1);
            token.waitForCompletion(5000);
            int[] granted = token.getGrantedQos();
            assertEquals(1, granted.length);
            assertTrue(granted[0] >= 128);
        } catch (MqttException expected) {
            assertTrue(true);
        }
    }

    private void rollback(
            MosquittoDynamicSecurityClient broker,
            boolean clientCreated,
            boolean roleCreated
    ) {
        if (clientCreated) {
            broker.revokeDeviceIdentity(BackendMqttServicePolicy.USERNAME);
        }
        if (roleCreated) {
            broker.deleteRole(BackendMqttServicePolicy.ROLE_NAME);
        }
    }

    private boolean containsName(JsonNode entries, String field, String value) {
        for (JsonNode entry : entries) {
            if (value.equals(entry.path(field).asText())) {
                return true;
            }
        }
        return false;
    }

    private ObjectNode command(ObjectMapper mapper, String name) {
        ObjectNode command = mapper.createObjectNode();
        command.put("command", name);
        return command;
    }

    private void requireMutationAuthorization() {
        if (!"true".equalsIgnoreCase(System.getProperty("nexaiot.integration.backend-provisioning"))
                || !"true".equalsIgnoreCase(System.getProperty("nexaiot.integration.backend-mutations"))) {
            throw new IllegalStateException("Two explicit backend provisioning flags are required.");
        }
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for controlled provisioning.");
        }
        return value;
    }
}
