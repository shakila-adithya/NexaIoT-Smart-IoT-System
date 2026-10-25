package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class BackendMqttServicePolicyTest {

    @Test
    void definesOnlyRequiredBackendAclEntries() {
        List<ObjectNode> commands = BackendMqttServicePolicy.roleCommands(new ObjectMapper());

        assertEquals(11, commands.size());
        assertEquals("createRole", commands.get(0).path("command").asText());
        assertEquals(BackendMqttServicePolicy.ROLE_NAME,
                commands.get(0).path("rolename").asText());

        Set<String> aclTypes = commands.subList(1, commands.size()).stream()
                .map(command -> command.path("acltype").asText())
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "subscribePattern",
                "publishClientReceive",
                "unsubscribePattern",
                "publishClientSend"), aclTypes);
        assertTrue(commands.subList(1, commands.size()).stream()
                .allMatch(command -> command.path("allow").asBoolean()
                        && command.path("priority").asInt() == 100));
        assertTrue(commands.stream().noneMatch(command ->
                command.path("topic").asText().startsWith("$CONTROL")));
        assertTrue(commands.stream().noneMatch(command ->
                command.path("topic").asText().equals("#")));
    }

    @Test
    void generatedBackendCredentialUsesDedicatedUsernameAndUniquePassword() {
        BackendServiceCredentialGenerator generator = new BackendServiceCredentialGenerator();
        GeneratedDeviceCredential first = generator.generate("nexaiot-backend-lab-client-1");
        GeneratedDeviceCredential second = generator.generate("nexaiot-backend-lab-client-2");

        assertEquals(BackendMqttServicePolicy.USERNAME, first.username());
        assertEquals("nexaiot-backend-lab-client-1", first.clientId());
        assertNotEquals(first.password(), second.password());
        assertTrue(first.toString().contains("password=<redacted>"));
        assertTrue(!first.toString().contains(first.password()));
    }
}
