package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class MosquittoDynamicSecurityClientIdentityTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsAdministratorWithoutFixedClientId() throws Exception {
        BrokerClientInspection inspection = parse(
                "{\"data\":{\"client\":{\"username\":\"admin\",\"roles\":[],\"groups\":[]}}}",
                "admin");

        assertEquals("admin", inspection.username());
        assertNull(inspection.clientId());
        assertFalse(inspection.clientIdPresent());
        assertTrue(inspection.acknowledged());
    }

    @Test
    void acceptsDeviceWithFixedClientId() throws Exception {
        BrokerClientInspection inspection = parse(
                "{\"data\":{\"client\":{\"username\":\"device\",\"clientid\":\"device-client\",\"roles\":[],\"groups\":[]}}}",
                "device");

        assertEquals("device-client", inspection.clientId());
        assertTrue(inspection.clientIdPresent());
    }

    @Test
    void rejectsDeviceWithoutFixedClientId() throws Exception {
        BrokerClientInspection inspection = parse(
                "{\"data\":{\"client\":{\"username\":\"device\",\"roles\":[],\"groups\":[]}}}",
                "device");

        assertThrows(BrokerManagementException.class,
                () -> requireFixedClientId(inspection));
    }

    @Test
    void rejectsMalformedClientResponse() throws Exception {
        assertThrows(BrokerManagementException.class,
                () -> parse(mapper.readTree("{\"data\":{\"client\":{\"username\":\"device\",\"clientid\":7}}}"), "device"));
    }

    @Test
    void preservesRoleAndDefaultAclReadOnlyContracts() throws Exception {
        JsonNode role = mapper.readTree("{\"data\":{\"role\":{\"rolename\":\"dynsec-admin\"}}}");
        JsonNode defaults = mapper.readTree("{\"data\":{\"acls\":["
                + "{\"acltype\":\"publishClientSend\",\"allow\":false},"
                + "{\"acltype\":\"publishClientReceive\",\"allow\":false},"
                + "{\"acltype\":\"subscribe\",\"allow\":false},"
                + "{\"acltype\":\"unsubscribe\",\"allow\":false}]}}");

        assertEquals("dynsec-admin", role.path("data").path("role").path("rolename").asText());
        assertEquals(4, defaults.path("data").path("acls").size());
    }

    private BrokerClientInspection parse(String json, String username) throws Exception {
        return parse(mapper.readTree(json), username);
    }

    private BrokerClientInspection parse(JsonNode response, String username) {
        return MosquittoDynamicSecurityClient.parseClientInspection(response, username);
    }

    private void requireFixedClientId(BrokerClientInspection inspection) {
        if (!inspection.clientIdPresent()) {
            throw new BrokerManagementException(
                    BrokerManagementFailure.INVALID_RESPONSE,
                    "Broker returned an incomplete client identity.");
        }
    }
}
