package com.nexaiot.server.mqtt.management;

import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexaiot.server.mqtt.MqttTopicSecurity;

final class BrokerProvisioningVerifier {

    private BrokerProvisioningVerifier() {
    }

    static BrokerDeviceProvisioningStatus verify(
            JsonNode clientResponse,
            JsonNode roleResponse,
            String deviceKey
    ) {
        MqttTopicSecurity.validateDeviceKey(deviceKey);
        String expectedUsername = "nexaiot-device-" + deviceKey;
        String expectedRole = expectedUsername;
        JsonNode client = clientResponse == null ? null : clientResponse.path("data").path("client");
        JsonNode role = roleResponse == null ? null : roleResponse.path("data").path("role");
        if (client == null || role == null || !client.isObject() || !role.isObject()
                || !expectedUsername.equals(client.path("username").asText())
                || !expectedUsername.equals(client.path("clientid").asText())
                || !expectedRole.equals(role.path("rolename").asText())
                || !client.path("roles").isArray()
                || !client.path("groups").isArray()
                || !role.path("acls").isArray()) {
            throw invalid("Broker device identity or assigned role is incomplete.");
        }
        requireExactRoles(client.path("roles"), expectedRole);
        requireNoGroups(client.path("groups"));
        JsonNode disabled = client.get("disabled");
        if ((disabled != null && !disabled.isBoolean())
                || (disabled != null && disabled.asBoolean())) {
            throw invalid("Broker device identity is disabled or malformed.");
        }

        Set<Acl> expected = expectedAcls(deviceKey);
        Set<Acl> actual = new LinkedHashSet<>();
        for (JsonNode acl : role.path("acls")) {
            if (!acl.isObject() || !acl.path("acltype").isTextual()
                    || acl.path("acltype").asText().isBlank()
                    || !acl.path("topic").isTextual() || acl.path("topic").asText().isBlank()
                    || !acl.path("allow").isBoolean()
                    || !acl.path("priority").canConvertToInt()) {
                throw invalid("Broker role ACL entry is malformed.");
            }
            actual.add(new Acl(acl.path("acltype").asText(), acl.path("topic").asText(),
                    acl.path("allow").asBoolean(), acl.path("priority").asInt()));
        }
        if (actual.size() != role.path("acls").size()) {
            throw invalid("Broker role contains duplicate ACL entries.");
        }
        if (!actual.equals(expected)) {
            throw invalid("Broker role ACL set is not exactly the expected set.");
        }
        return new BrokerDeviceProvisioningStatus(
                client.path("username").asText(), client.path("clientid").asText(),
                role.path("rolename").asText(), expected.size(),
                (int) expected.stream().filter(actual::contains).count(),
                true, true);
    }

    private static Set<Acl> expectedAcls(String deviceKey) {
        String telemetry = "nexaiot/devices/" + deviceKey + "/telemetry";
        String state = "nexaiot/devices/" + deviceKey + "/state";
        String commandResults = "nexaiot/devices/" + deviceKey + "/command-results";
        String command = MqttTopicSecurity.commandTopic(deviceKey);
        Set<Acl> expected = new LinkedHashSet<>();
        expected.add(new Acl("publishClientSend", telemetry, true, 100));
        expected.add(new Acl("publishClientSend", state, true, 100));
        expected.add(new Acl("publishClientSend", commandResults, true, 100));
        expected.add(new Acl("subscribeLiteral", command, true, 100));
        expected.add(new Acl("publishClientReceive", command, true, 100));
        for (String topic : Set.of("#", "$CONTROL/dynamic-security/#", "$SYS/#")) {
            expected.add(new Acl("publishClientSend", topic, false, 1));
            expected.add(new Acl("publishClientReceive", topic, false, 1));
        }
        return expected;
    }

    private static void requireExactRoles(JsonNode roles, String expectedRole) {
        Set<String> actual = new HashSet<>();
        for (JsonNode role : roles) {
            if (!role.isObject() || !role.path("rolename").isTextual()
                    || role.path("rolename").asText().isBlank()
                    || (role.has("priority") && !role.path("priority").canConvertToInt())
                    || !actual.add(role.path("rolename").asText())) {
                throw invalid("Broker client role data is malformed or duplicated.");
            }
        }
        if (!actual.equals(Set.of(expectedRole))) {
            throw invalid("Broker client has an unexpected role assignment.");
        }
    }

    private static void requireNoGroups(JsonNode groups) {
        Set<String> actual = new HashSet<>();
        for (JsonNode group : groups) {
            if (!group.isObject() || !group.path("groupname").isTextual()
                    || group.path("groupname").asText().isBlank()
                    || (group.has("priority") && !group.path("priority").canConvertToInt())
                    || !actual.add(group.path("groupname").asText())) {
                throw invalid("Broker client group data is malformed or duplicated.");
            }
        }
        if (!actual.isEmpty()) {
            throw invalid("Broker client has unexpected group membership.");
        }
    }

    private static BrokerManagementException invalid(String message) {
        return new BrokerManagementException(BrokerManagementFailure.INVALID_RESPONSE, message);
    }

    private record Acl(String type, String topic, boolean allow, int priority) {
    }
}
