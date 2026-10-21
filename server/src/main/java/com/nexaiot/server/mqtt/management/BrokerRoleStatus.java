package com.nexaiot.server.mqtt.management;

import java.util.List;

public record BrokerRoleStatus(
        String roleName,
        List<String> referencedClients,
        List<String> referencedGroups,
        boolean referencesKnown,
        boolean acknowledged
) {
    public BrokerRoleStatus {
        referencedClients = List.copyOf(referencedClients);
        referencedGroups = List.copyOf(referencedGroups);
    }
}
