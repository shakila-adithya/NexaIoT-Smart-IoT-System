package com.nexaiot.server.mqtt.management;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/** Strict, offline-testable validation of a Mosquitto Dynamic Security inventory. */
final class BrokerReconciliationVerifier {

    static final String DISPOSABLE = "nexaiot-device-NEXA-8CD8C0F1559E";
    private static final String MANAGEMENT = "nexaiot-provisioning-management";
    private static final String OBSERVER = "nexaiot-b35c-observer-NEXA-8CD8C0F1559E";
    private static final String ADMIN = "nexaiot-tls-admin";
    private static final String MANAGEMENT_ROLE = "nexaiot-provisioning-management-role";
    private static final String OBSERVER_ROLE = "nexaiot-b35c-observer-role-NEXA-8CD8C0F1559E";

    private BrokerReconciliationVerifier() {
    }

    static void verify(
            JsonNode clients,
            JsonNode roles,
            JsonNode groups,
            JsonNode defaultAcl
    ) {
        Set<String> clientNames = verifyClients(clients);
        if (clientNames.contains(DISPOSABLE)) {
            throw invalid("Disposable client is present.");
        }
        verifyRoles(roles);
        verifyGroups(groups);
        verifyDefaultAcl(defaultAcl);
    }

    private static Set<String> verifyClients(JsonNode data) {
        requireCompleteList(data, "clients");
        Set<String> names = new HashSet<>();
        for (JsonNode client : data.path("clients")) {
            if (!client.isObject() || !client.path("username").isTextual()
                    || client.path("username").asText().isBlank()
                    || !client.path("roles").isArray() || !client.path("groups").isArray()) {
                throw invalid("Client inventory entry is malformed.");
            }
            String username = client.path("username").asText();
            if (!names.add(username)) {
                throw invalid("Client inventory contains a duplicate username.");
            }
            if (ADMIN.equals(username)) {
                requireRole(client, "admin");
            } else if (MANAGEMENT.equals(username)) {
                requireClientId(client, MANAGEMENT);
                requireRole(client, MANAGEMENT_ROLE);
            } else if (OBSERVER.equals(username)) {
                requireClientId(client, OBSERVER);
                requireRole(client, OBSERVER_ROLE);
            } else {
                throw invalid("Unexpected client inventory entry.");
            }
            for (JsonNode role : client.path("roles")) {
                if (!role.isObject() || !role.path("rolename").isTextual()
                        || role.path("rolename").asText().isBlank()) {
                    throw invalid("Client role reference is malformed.");
                }
            }
            if (client.path("groups").size() != 0) {
                throw invalid("Unexpected client group membership.");
            }
        }
        if (!names.equals(Set.of(ADMIN, MANAGEMENT, OBSERVER))) {
            throw invalid("Client inventory is incomplete or unexpected.");
        }
        return names;
    }

    private static void verifyRoles(JsonNode data) {
        requireCompleteList(data, "roles");
        Set<String> names = new HashSet<>();
        for (JsonNode role : data.path("roles")) {
            if (!role.isObject() || !role.path("rolename").isTextual()
                    || role.path("rolename").asText().isBlank()
                    || !names.add(role.path("rolename").asText())) {
                throw invalid("Role inventory is malformed or duplicated.");
            }
        }
        if (!names.equals(Set.of("admin", MANAGEMENT_ROLE, OBSERVER_ROLE))) {
            throw invalid("Role inventory is incomplete or unexpected.");
        }
        if (names.contains(DISPOSABLE)) {
            throw invalid("Disposable role is present.");
        }
    }

    private static void verifyGroups(JsonNode data) {
        requireCompleteList(data, "groups");
        for (JsonNode group : data.path("groups")) {
            if (!group.isObject() || !group.path("groupname").isTextual()
                    || group.path("groupname").asText().isBlank()) {
                throw invalid("Group inventory entry is malformed.");
            }
            throw invalid("Unexpected broker group is present.");
        }
    }

    private static void verifyDefaultAcl(JsonNode data) {
        if (data == null || !data.isObject()) {
            throw invalid("Default ACL response is malformed.");
        }
        JsonNode entries = data.get("acls");
        if (entries == null || !entries.isArray() || entries.size() != 4) {
            throw invalid("Default ACL response must contain exactly four ACL entries.");
        }
        Map<String, Boolean> expected = Map.of(
                "publishClientSend", false,
                "publishClientReceive", false,
                "subscribe", false,
                "unsubscribe", true);
        Set<String> seen = new HashSet<>();
        for (JsonNode entry : entries) {
            if (!entry.isObject() || !entry.path("acltype").isTextual()
                    || !entry.path("allow").isBoolean()) {
                throw invalid("Default ACL entry is malformed.");
            }
            String aclType = entry.path("acltype").asText();
            if (!expected.containsKey(aclType) || !seen.add(aclType)) {
                throw invalid("Default ACL entry is unexpected or duplicated.");
            }
            if (entry.path("allow").asBoolean() != expected.get(aclType)) {
                throw invalid("Default ACL mismatch for " + aclType + ".");
            }
        }
        if (!seen.equals(expected.keySet())) {
            throw invalid("Default ACL response is incomplete.");
        }
    }

    private static void requireRole(JsonNode client, String roleName) {
        for (JsonNode role : client.path("roles")) {
            if (role.isObject() && roleName.equals(role.path("rolename").asText())) {
                return;
            }
        }
        throw invalid("Expected client role reference is missing.");
    }

    private static void requireClientId(JsonNode client, String expected) {
        if (!client.path("clientid").isTextual() || !expected.equals(client.path("clientid").asText())) {
            throw invalid("Expected client ID is missing or unexpected.");
        }
    }

    private static void requireCompleteList(JsonNode data, String field) {
        JsonNode entries = data == null ? null : data.get(field);
        JsonNode totalCount = data == null ? null : data.get("totalCount");
        if (entries == null || !entries.isArray() || totalCount == null
                || !totalCount.isIntegralNumber() || !totalCount.canConvertToInt()
                || totalCount.asInt() < 0 || totalCount.asInt() != entries.size()) {
            throw invalid("Inventory list is incomplete or has an invalid totalCount.");
        }
    }

    private static BrokerManagementException invalid(String message) {
        return new BrokerManagementException(BrokerManagementFailure.INVALID_RESPONSE, message);
    }
}
