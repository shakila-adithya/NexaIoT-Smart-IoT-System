package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class BrokerReconciliationVerifierTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsCompleteExpectedInventory() throws Exception {
        assertDoesNotThrow(() -> BrokerReconciliationVerifier.verify(
                mapper.readTree("{\"totalCount\":3,\"clients\":["
                        + "{\"username\":\"nexaiot-tls-admin\",\"roles\":[{\"rolename\":\"admin\"}],\"groups\":[]},"
                        + "{\"username\":\"nexaiot-provisioning-management\",\"clientid\":\"nexaiot-provisioning-management\",\"roles\":[{\"rolename\":\"nexaiot-provisioning-management-role\"}],\"groups\":[]},"
                        + "{\"username\":\"nexaiot-b35c-observer-NEXA-8CD8C0F1559E\",\"clientid\":\"nexaiot-b35c-observer-NEXA-8CD8C0F1559E\",\"roles\":[{\"rolename\":\"nexaiot-b35c-observer-role-NEXA-8CD8C0F1559E\"}],\"groups\":[]}] }"),
                mapper.readTree("{\"totalCount\":3,\"roles\":[{\"rolename\":\"admin\"},{\"rolename\":\"nexaiot-provisioning-management-role\"},{\"rolename\":\"nexaiot-b35c-observer-role-NEXA-8CD8C0F1559E\"}]}"),
                mapper.readTree("{\"totalCount\":0,\"groups\":[]}"),
                mapper.readTree("{\"acls\":[{\"acltype\":\"publishClientSend\",\"allow\":false},{\"acltype\":\"publishClientReceive\",\"allow\":false},{\"acltype\":\"subscribe\",\"allow\":false},{\"acltype\":\"unsubscribe\",\"allow\":true}]}")));
    }

    @Test
    void rejectsStaleDisposableClient() throws Exception {
        JsonNode clients = mapper.readTree("{\"totalCount\":4,\"clients\":["
                + "{\"username\":\"nexaiot-tls-admin\",\"roles\":[{\"rolename\":\"admin\"}],\"groups\":[]},"
                + "{\"username\":\"nexaiot-provisioning-management\",\"clientid\":\"nexaiot-provisioning-management\",\"roles\":[{\"rolename\":\"nexaiot-provisioning-management-role\"}],\"groups\":[]},"
                + "{\"username\":\"nexaiot-b35c-observer-NEXA-8CD8C0F1559E\",\"clientid\":\"nexaiot-b35c-observer-NEXA-8CD8C0F1559E\",\"roles\":[{\"rolename\":\"nexaiot-b35c-observer-role-NEXA-8CD8C0F1559E\"}],\"groups\":[]},"
                + "{\"username\":\"nexaiot-device-NEXA-8CD8C0F1559E\",\"clientid\":\"nexaiot-device-NEXA-8CD8C0F1559E\",\"roles\":[],\"groups\":[]}]}");
        assertThrows(BrokerManagementException.class, () -> BrokerReconciliationVerifier.verify(
                clients,
                mapper.readTree("{\"totalCount\":3,\"roles\":[]}"),
                mapper.readTree("{\"totalCount\":0,\"groups\":[]}"),
                mapper.readTree("{\"acls\":[{\"acltype\":\"publishClientSend\",\"allow\":false},{\"acltype\":\"publishClientReceive\",\"allow\":false},{\"acltype\":\"subscribe\",\"allow\":false},{\"acltype\":\"unsubscribe\",\"allow\":true}]}")));
    }

    @Test
    void rejectsMosquittoAclArrayWithUnexpectedDefault() throws Exception {
        assertThrows(BrokerManagementException.class, () -> BrokerReconciliationVerifier.verify(
                expectedClients(), expectedRoles(), mapper.readTree("{\"totalCount\":0,\"groups\":[]}"),
                mapper.readTree("{\"acls\":[{\"acltype\":\"publishClientSend\",\"allow\":false},{\"acltype\":\"publishClientReceive\",\"allow\":true},{\"acltype\":\"subscribe\",\"allow\":false},{\"acltype\":\"unsubscribe\",\"allow\":true}]}")));
    }

    private JsonNode expectedClients() throws Exception {
        return mapper.readTree("{\"totalCount\":3,\"clients\":["
                + "{\"username\":\"nexaiot-tls-admin\",\"roles\":[{\"rolename\":\"admin\"}],\"groups\":[]},"
                + "{\"username\":\"nexaiot-provisioning-management\",\"clientid\":\"nexaiot-provisioning-management\",\"roles\":[{\"rolename\":\"nexaiot-provisioning-management-role\"}],\"groups\":[]},"
                + "{\"username\":\"nexaiot-b35c-observer-NEXA-8CD8C0F1559E\",\"clientid\":\"nexaiot-b35c-observer-NEXA-8CD8C0F1559E\",\"roles\":[{\"rolename\":\"nexaiot-b35c-observer-role-NEXA-8CD8C0F1559E\"}],\"groups\":[]}]}");
    }

    private JsonNode expectedRoles() throws Exception {
        return mapper.readTree("{\"totalCount\":3,\"roles\":[{\"rolename\":\"admin\"},{\"rolename\":\"nexaiot-provisioning-management-role\"},{\"rolename\":\"nexaiot-b35c-observer-role-NEXA-8CD8C0F1559E\"}]}");
    }
}
