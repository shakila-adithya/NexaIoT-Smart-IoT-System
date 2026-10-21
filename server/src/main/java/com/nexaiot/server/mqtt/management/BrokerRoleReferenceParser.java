package com.nexaiot.server.mqtt.management;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

final class BrokerRoleReferenceParser {

    private static final Pattern SAFE_ROLE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");

    private BrokerRoleReferenceParser() {
    }

    static BrokerRoleStatus parse(
            String roleName,
            JsonNode clientData,
            JsonNode groupData
    ) {
        requireRoleName(roleName);
        if (clientData == null || groupData == null
                || !clientData.isObject() || !groupData.isObject()) {
            throw invalid("Complete client and group role references are required.");
        }
        requireCompleteList(clientData, "clients");
        requireCompleteList(groupData, "groups");

        List<String> clients = new ArrayList<>();
        Set<String> clientNames = new HashSet<>();
        for (JsonNode client : clientData.path("clients")) {
            requireOwnerShape(client, "username");
            String username = client.path("username").asText();
            if (!clientNames.add(username)) {
                throw invalid("Duplicate broker client reference entry.");
            }
            if (hasRole(client.path("roles"), roleName)) {
                clients.add(username);
            }
        }

        List<String> groups = new ArrayList<>();
        Set<String> groupNames = new HashSet<>();
        for (JsonNode group : groupData.path("groups")) {
            requireOwnerShape(group, "groupname");
            String groupName = group.path("groupname").asText();
            if (!groupNames.add(groupName)) {
                throw invalid("Duplicate broker group reference entry.");
            }
            if (hasRole(group.path("roles"), roleName)) {
                groups.add(groupName);
            }
        }
        return new BrokerRoleStatus(roleName, clients, groups, true, true);
    }

    private static void requireOwnerShape(JsonNode owner, String nameField) {
        if (!owner.isObject() || !owner.path(nameField).isTextual()
                || owner.path(nameField).asText().isBlank()
                || !owner.path("roles").isArray()) {
            throw invalid("Broker role reference entry is malformed.");
        }
        for (JsonNode role : owner.path("roles")) {
            if (!role.isObject() || !role.path("rolename").isTextual()
                    || role.path("rolename").asText().isBlank()) {
                throw invalid("Broker role reference role entry is malformed.");
            }
            if (role.has("priority") && !role.path("priority").canConvertToInt()) {
                throw invalid("Broker role reference role priority is malformed.");
            }
        }
    }

    private static void requireCompleteList(JsonNode data, String field) {
        JsonNode entries = data.get(field);
        JsonNode totalCount = data.get("totalCount");
        if (entries == null || !entries.isArray() || totalCount == null || !totalCount.isIntegralNumber()
                || !totalCount.canConvertToInt() || totalCount.asInt() < 0
                || totalCount.asInt() != entries.size()) {
            throw invalid("Broker role reference list is incomplete or has an invalid totalCount.");
        }
    }

    private static void requireRoleName(String roleName) {
        if (roleName == null || !SAFE_ROLE_NAME.matcher(roleName).matches()) {
            throw invalid("Broker role name is invalid.");
        }
    }

    private static boolean hasRole(JsonNode roles, String roleName) {
        for (JsonNode role : roles) {
            if (roleName.equals(role.path("rolename").asText())) {
                return true;
            }
        }
        return false;
    }

    private static BrokerManagementException invalid(String message) {
        return new BrokerManagementException(BrokerManagementFailure.INVALID_RESPONSE, message);
    }
}
