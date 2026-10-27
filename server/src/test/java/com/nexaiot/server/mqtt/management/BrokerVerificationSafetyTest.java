package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class BrokerVerificationSafetyTest {

    @Test
    void mapsMqttConnectRefusalsToActionableManagementFailures() {
        assertEquals(BrokerManagementFailure.AUTHENTICATION_FAILED,
                MosquittoDynamicSecurityClient.connectionFailure(4));
        assertEquals(BrokerManagementFailure.UNAUTHORIZED,
                MosquittoDynamicSecurityClient.connectionFailure(5));
        assertEquals(BrokerManagementFailure.CONNECTION_FAILED,
                MosquittoDynamicSecurityClient.connectionFailure(8));
    }

    private static final String DEVICE_KEY = "NEXA-8CD8C0F1559E";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requiresBothCompleteReferenceLists() throws Exception {
        JsonNode clients = mapper.readTree("{\"totalCount\":0,\"clients\":[]}");
        JsonNode groups = mapper.readTree("{\"totalCount\":0,\"groups\":[]}");
        BrokerRoleStatus status = BrokerRoleReferenceParser.parse("role", clients, groups);
        assertTrue(status.referencesKnown());
        assertTrue(status.referencedClients().isEmpty());

        assertThrows(BrokerManagementException.class,
                () -> BrokerRoleReferenceParser.parse("role", clients,
                        mapper.readTree("{}")));
        assertThrows(BrokerManagementException.class,
                () -> BrokerRoleReferenceParser.parse("role",
                        mapper.readTree("{\"clients\":[{\"username\":\"u\",\"roles\":null}]}"),
                        groups));
    }

    @Test
    void rejectsTruncatedClientList() throws Exception {
        assertThrows(BrokerManagementException.class, () -> BrokerRoleReferenceParser.parse(
                "role", mapper.readTree("{\"totalCount\":2,\"clients\":[{\"username\":\"u\",\"roles\":[]}]}"),
                mapper.readTree("{\"totalCount\":0,\"groups\":[]}")));
    }

    @Test
    void rejectsTruncatedGroupList() throws Exception {
        assertThrows(BrokerManagementException.class, () -> BrokerRoleReferenceParser.parse(
                "role", mapper.readTree("{\"totalCount\":0,\"clients\":[]}"),
                mapper.readTree("{\"totalCount\":2,\"groups\":[{\"groupname\":\"g\",\"roles\":[]}]}")));
    }

    @Test
    void rejectsInvalidTotalCount() throws Exception {
        assertThrows(BrokerManagementException.class, () -> BrokerRoleReferenceParser.parse(
                "role", mapper.readTree("{\"totalCount\":-1,\"clients\":[]}"),
                mapper.readTree("{\"totalCount\":0,\"groups\":[]}")));
        assertThrows(BrokerManagementException.class, () -> BrokerRoleReferenceParser.parse(
                "role", mapper.readTree("{\"totalCount\":\"0\",\"clients\":[]}"),
                mapper.readTree("{\"totalCount\":0,\"groups\":[]}")));
    }

    @Test
    void rejectsDuplicateClientReferences() throws Exception {
        String entry = "{\"username\":\"u\",\"roles\":[]}";
        assertThrows(BrokerManagementException.class, () -> BrokerRoleReferenceParser.parse(
                "role", mapper.readTree("{\"totalCount\":2,\"clients\":[" + entry + "," + entry + "]}"),
                mapper.readTree("{\"totalCount\":0,\"groups\":[]}")));
    }

    @Test
    void rejectsRoleUsedByUnrelatedClientOrGroup() {
        IntegrationRunReceipt receipt = receipt();
        BrokerRoleStatus status = new BrokerRoleStatus(
                receipt.roleName(), List.of(receipt.username()), List.of("unrelated-group"),
                true, true);
        assertThrows(IllegalStateException.class,
                () -> IntegrationResourceOwnership.requireRoleOwnership(receipt, status));
    }

    @Test
    void verifiesAssignedRoleAndExpectedAclSet() throws Exception {
        JsonNode clientResponse = mapper.readTree("""
                {"data":{"client":{"username":"nexaiot-device-NEXA-8CD8C0F1559E",
                "clientid":"nexaiot-device-NEXA-8CD8C0F1559E",
                "roles":[{"rolename":"nexaiot-device-NEXA-8CD8C0F1559E"}],"groups":[]}}}
                """);
        JsonNode roleResponse = mapper.readTree(roleFixture(false));

        BrokerDeviceProvisioningStatus status = BrokerProvisioningVerifier.verify(
                clientResponse, roleResponse, DEVICE_KEY);
        assertTrue(status.verified());
        assertEquals(14, status.verifiedAclCount());
        assertTrue(status.noUnexpectedAllowRules());
    }

    @Test
    void rejectsUnexpectedAllowedAcl() throws Exception {
        assertThrows(BrokerManagementException.class,
                () -> BrokerProvisioningVerifier.verify(
                        mapper.readTree("""
                                {"data":{"client":{"username":"nexaiot-device-NEXA-8CD8C0F1559E",
                                "clientid":"nexaiot-device-NEXA-8CD8C0F1559E",
                                "roles":[{"rolename":"nexaiot-device-NEXA-8CD8C0F1559E"}],"groups":[]}}}
                                """),
                        mapper.readTree(roleFixture(true)), DEVICE_KEY));
    }

    @Test
    void rejectsCrossDeviceCommandResultPermission() throws Exception {
        JsonNode role = mapper.readTree(roleFixture(false));
        ArrayNode acls = (ArrayNode) role.path("data").path("role").path("acls");
        acl(acls, "publishClientSend", "nexaiot/devices/NEXA-OTHER/command-results",
                true, 100);
        assertThrows(BrokerManagementException.class,
                () -> BrokerProvisioningVerifier.verify(
                        clientFixture("[{\"rolename\":\"nexaiot-device-NEXA-8CD8C0F1559E\"}]", "[]"),
                        role, DEVICE_KEY));
    }

    @Test
    void rejectsUnexpectedClientRole() throws Exception {
        assertThrows(BrokerManagementException.class, () -> BrokerProvisioningVerifier.verify(
                clientFixture("[{\"rolename\":\"nexaiot-device-NEXA-8CD8C0F1559E\"},{\"rolename\":\"other\"}]", "[]"),
                mapper.readTree(roleFixture(false)), DEVICE_KEY));
    }

    @Test
    void rejectsUnexpectedGroupMembership() throws Exception {
        assertThrows(BrokerManagementException.class, () -> BrokerProvisioningVerifier.verify(
                clientFixture("[{\"rolename\":\"nexaiot-device-NEXA-8CD8C0F1559E\"}]", "[{\"groupname\":\"extra\"}]"),
                mapper.readTree(roleFixture(false)), DEVICE_KEY));
    }

    @Test
    void rejectsIncorrectMqttClientId() throws Exception {
        assertThrows(BrokerManagementException.class, () -> BrokerProvisioningVerifier.verify(
                clientFixture("[{\"rolename\":\"nexaiot-device-NEXA-8CD8C0F1559E\"}]", "[]", "wrong-client-id"),
                mapper.readTree(roleFixture(false)), DEVICE_KEY));
    }

    @Test
    void receiptContainsNoCredentialMaterial() throws Exception {
        IntegrationRunReceipt receipt = receipt();
        Path tempDirectory = Path.of("target", "receipt-test-" + UUID.randomUUID());
        Files.createDirectories(tempDirectory);
        try {
            Path path = IntegrationRunReceiptStore.write(tempDirectory, receipt);
            String contents = Files.readString(path);
            assertTrue(!contents.contains("password"));
            assertTrue(!contents.contains("secret"));
            assertTrue(contents.contains(receipt.runId()));
        } finally {
            Files.deleteIfExists(tempDirectory.resolve(receipt.runId() + ".receipt"));
            Files.deleteIfExists(tempDirectory);
        }
    }

    @Test
    void ownershipRequiresCreationEvidenceBeforeMutation() {
        IntegrationRunReceipt receipt = receipt();
        BrokerDeviceIdentityStatus actual = new BrokerDeviceIdentityStatus(
                receipt.username(), receipt.clientId(), false, true);
        assertThrows(IllegalStateException.class,
                () -> IntegrationResourceOwnership.requireClientOwnership(receipt, actual));
    }

    @Test
    void rejectsMissingOrMalformedIdentityFields() throws Exception {
        JsonNode validRole = mapper.readTree(roleFixture(false));
        assertThrows(BrokerManagementException.class,
                () -> BrokerProvisioningVerifier.verify(
                        mapper.readTree("{\"data\":{\"client\":{\"clientid\":\"id\","
                                + "\"roles\":[]}}}"), validRole, DEVICE_KEY));
        assertThrows(BrokerManagementException.class,
                () -> BrokerProvisioningVerifier.verify(
                        mapper.readTree("{\"data\":{\"client\":{\"username\":"
                                + "\"nexaiot-device-NEXA-8CD8C0F1559E\",\"clientid\":\"id\","
                                + "\"disabled\":\"false\",\"roles\":[{\"rolename\":"
                                + "\"nexaiot-device-NEXA-8CD8C0F1559E\"}]}}}"),
                        validRole, DEVICE_KEY));
    }

    @Test
    void partialRoleCreationCannotBeDeletedWithoutConfirmedEvidence() {
        IntegrationRunReceipt receipt = receipt();
        BrokerRoleStatus status = new BrokerRoleStatus(
                receipt.roleName(), List.of(), List.of(), true, true);
        assertThrows(IllegalStateException.class,
                () -> IntegrationResourceOwnership.requireRoleOwnership(receipt, status));
    }

    private IntegrationRunReceipt receipt() {
        return IntegrationRunReceipt.create(
                DEVICE_KEY,
                "nexaiot-device-" + DEVICE_KEY,
                "nexaiot-device-" + DEVICE_KEY,
                "nexaiot-device-" + DEVICE_KEY);
    }

    private String roleFixture(boolean unexpectedAllow) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode data = root.putObject("data");
        ObjectNode role = data.putObject("role");
        role.put("rolename", "nexaiot-device-" + DEVICE_KEY);
        ArrayNode acls = role.putArray("acls");
        acl(acls, "publishClientSend", "nexaiot/devices/" + DEVICE_KEY + "/telemetry", true, 100);
        acl(acls, "publishClientSend", "nexaiot/devices/" + DEVICE_KEY + "/state", true, 100);
        acl(acls, "publishClientSend", "nexaiot/devices/" + DEVICE_KEY + "/command-results", true, 100);
        acl(acls, "subscribeLiteral", "nexaiot/devices/" + DEVICE_KEY + "/commands", true, 100);
        acl(acls, "publishClientReceive", "nexaiot/devices/" + DEVICE_KEY + "/commands", true, 100);
        acl(acls, "publishClientSend", "nexaiot/devices/" + DEVICE_KEY + "/activation-responses", true, 100);
        acl(acls, "subscribeLiteral", "nexaiot/devices/" + DEVICE_KEY + "/activation-challenges", true, 100);
        acl(acls, "publishClientReceive", "nexaiot/devices/" + DEVICE_KEY + "/activation-challenges", true, 100);
        for (String topic : List.of("#", "$CONTROL/dynamic-security/#", "$SYS/#")) {
            acl(acls, "publishClientSend", topic, false, 1);
            acl(acls, "publishClientReceive", topic, false, 1);
        }
        if (unexpectedAllow) {
            acl(acls, "publishClientSend", "nexaiot/admin/#", true, 100);
        }
        return root.toString();
    }

    private JsonNode clientFixture(String roles, String groups) throws Exception {
        return clientFixture(roles, groups, "nexaiot-device-" + DEVICE_KEY);
    }

    private JsonNode clientFixture(String roles, String groups, String clientId) throws Exception {
        return mapper.readTree("{\"data\":{\"client\":{\"username\":\"nexaiot-device-"
                + DEVICE_KEY + "\",\"clientid\":\"" + clientId + "\",\"roles\":"
                + roles + ",\"groups\":" + groups + "}}}");
    }

    private void acl(ArrayNode acls, String type, String topic, boolean allow, int priority) {
        ObjectNode acl = acls.addObject();
        acl.put("acltype", type);
        acl.put("topic", topic);
        acl.put("allow", allow);
        acl.put("priority", priority);
    }
}
