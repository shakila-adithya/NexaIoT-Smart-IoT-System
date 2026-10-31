package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class DynamicSecurityResponseProcessorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsSuccessfulClientCreationResponse() {
        assertSuccess("createClient", "client-created");
    }

    @Test
    void acceptsSuccessfulRoleCreationResponse() {
        assertSuccess("createRole", "role-created");
    }

    @Test
    void acceptsSuccessfulAclAssignmentResponse() {
        assertSuccess("addRoleACL", "acl-added");
    }

    @Test
    void acceptsSuccessfulRoleAssignmentResponse() {
        assertSuccess("addClientRole", "role-assigned");
    }

    @Test
    void acceptsSuccessfulRoleDeletionResponse() {
        assertSuccess("deleteRole", "role-deleted");
    }

    @Test
    void classifiesCommandRejection() {
        assertFailure("createClient", "Invalid command", BrokerManagementFailure.COMMAND_REJECTED);
    }

    @Test
    void classifiesDuplicateClientAndRole() {
        assertFailure("createClient", "Client already exists", BrokerManagementFailure.DUPLICATE_CLIENT);
        assertFailure("createRole", "Role already exists", BrokerManagementFailure.DUPLICATE_ROLE);
    }

    @Test
    void classifiesMissingRoleAsResourceNotFound() {
        assertFailure("getRole", "Role does not exist", BrokerManagementFailure.RESOURCE_NOT_FOUND);
    }

    @Test
    void rejectsMissingResponse() {
        DynamicSecurityResponseProcessor processor = processor("createClient", "client-created");
        processor.accept("{\"responses\":[]}".getBytes(StandardCharsets.UTF_8));

        BrokerManagementException exception = assertThrows(
                BrokerManagementException.class, processor::complete);
        assertEquals(BrokerManagementFailure.INVALID_RESPONSE, exception.getFailure());
    }

    @Test
    void rejectsIncorrectCorrelationId() {
        assertFailureWithCorrelation("createClient", "wrong-correlation", "OK",
                BrokerManagementFailure.INVALID_RESPONSE);
    }

    @Test
    void rejectsIncorrectCommandName() {
        assertFailureWithCorrelation("createClient", "client-created", "createRole",
                BrokerManagementFailure.INVALID_RESPONSE);
    }

    @Test
    void rejectsMalformedJson() {
        DynamicSecurityResponseProcessor processor = processor("createClient", "client-created");
        BrokerManagementException exception = assertThrows(
                BrokerManagementException.class,
                () -> processor.accept("{not-json".getBytes(StandardCharsets.UTF_8)));
        assertEquals(BrokerManagementFailure.INVALID_RESPONSE, exception.getFailure());
    }

    @Test
    void ignoresDuplicateResponsesAfterFirstCorrelationMatch() {
        DynamicSecurityResponseProcessor processor = processor("createClient", "client-created");
        byte[] response = response("createClient", "client-created", "OK");

        assertEquals(1, processor.accept(response));
        assertEquals(1, processor.accept(response));
        assertEquals(1, processor.complete().size());
    }

    @Test
    void completesAnElevenCommandBatchWithoutCountdownUnderflowOrTimeout() throws Exception {
        Map<String, String> expected = new LinkedHashMap<>();
        for (int index = 0; index < 11; index++) {
            expected.put("correlation-" + index, "command-" + index);
        }
        DynamicSecurityResponseProcessor processor = processorBatch(expected);
        CountDownLatch completion = new CountDownLatch(1);
        StringBuilder payload = new StringBuilder("{\"responses\":[");
        for (int index = 0; index < 11; index++) {
            if (index > 0) {
                payload.append(',');
            }
            payload.append(responseJson("command-" + index, "correlation-" + index, "OK"));
        }
        payload.append("]}");

        processor.accept(payload.toString().getBytes(StandardCharsets.UTF_8));
        if (processor.isComplete()) {
            completion.countDown();
        }

        assertEquals(true, completion.await(100, TimeUnit.MILLISECONDS));
        assertEquals(11, processor.complete().size());
    }

    @Test
    void completesElevenResponsesAcrossMessagesAndIgnoresDuplicates() throws Exception {
        Map<String, String> expected = new LinkedHashMap<>();
        for (int index = 0; index < 11; index++) {
            expected.put("correlation-" + index, "command-" + index);
        }
        DynamicSecurityResponseProcessor processor = processorBatch(expected);
        CountDownLatch completion = new CountDownLatch(1);
        for (int index = 0; index < 10; index++) {
            processor.accept(response("command-" + index, "correlation-" + index, "OK"));
            processor.accept(response("command-" + index, "correlation-" + index, "OK"));
            if (processor.isComplete()) {
                completion.countDown();
            }
        }
        processor.accept(response("command-10", "correlation-10", "OK"));
        if (processor.isComplete()) {
            completion.countDown();
        }

        assertEquals(true, completion.await(100, TimeUnit.MILLISECONDS));
        assertEquals(11, processor.complete().size());
    }

    @Test
    void classifiesPartialBatchFailure() {
        DynamicSecurityResponseProcessor processor = processorBatch(
                Map.of("client-created", "createClient", "role-created", "createRole"));
        String payload = "{\"responses\":["
                + responseJson("createClient", "client-created", "OK") + ","
                + responseJson("createRole", "role-created", "Role ACL rejected") + "]}";

        BrokerManagementException exception = assertThrows(
                BrokerManagementException.class,
                () -> processor.accept(payload.getBytes(StandardCharsets.UTF_8)));
        assertEquals(BrokerManagementFailure.COMMAND_REJECTED, exception.getFailure());
    }

    @Test
    void preservesBoundedTimeoutAndUnknownOutcomeClassifications() {
        assertEquals(BrokerManagementFailure.TIMEOUT, BrokerManagementFailure.valueOf("TIMEOUT"));
        assertEquals(BrokerManagementFailure.UNKNOWN_STATE, BrokerManagementFailure.valueOf("UNKNOWN_STATE"));
    }

    @Test
    void classifiesUnauthorizedManagementResponse() {
        assertFailure("createClient", "Not authorised", BrokerManagementFailure.UNAUTHORIZED);
    }

    private void assertSuccess(String command, String correlation) {
        DynamicSecurityResponseProcessor processor = processor(command, correlation);
        assertEquals(1, processor.accept(response(command, correlation, "OK")));
        assertEquals(1, processor.complete().size());
    }

    private void assertFailure(String command, String error, BrokerManagementFailure expected) {
        DynamicSecurityResponseProcessor processor = processor(command, "correlation");
        BrokerManagementException exception = assertThrows(
                BrokerManagementException.class,
                () -> processor.accept(response(command, "correlation", error)));
        assertEquals(expected, exception.getFailure());
    }

    private void assertFailureWithCorrelation(
            String expectedCommand,
            String correlation,
            String actualCommand,
            BrokerManagementFailure expectedFailure
    ) {
        DynamicSecurityResponseProcessor processor = processor(expectedCommand, "client-created");
        BrokerManagementException exception = assertThrows(
                BrokerManagementException.class,
                () -> processor.accept(response(actualCommand, correlation, "OK")));
        assertEquals(expectedFailure, exception.getFailure());
    }

    private DynamicSecurityResponseProcessor processor(String command, String correlation) {
        return processorBatch(Map.of(correlation, command));
    }

    private DynamicSecurityResponseProcessor processorBatch(Map<String, String> expected) {
        return new DynamicSecurityResponseProcessor(mapper, new LinkedHashMap<>(expected));
    }

    private byte[] response(String command, String correlation, String error) {
        return ("{\"responses\":[" + responseJson(command, correlation, error) + "]}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private String responseJson(String command, String correlation, String error) {
        ObjectNode response = mapper.createObjectNode();
        response.put("command", command);
        response.put("correlationData", correlation);
        if (!"OK".equals(error)) {
            response.put("error", error);
        }
        return response.toString();
    }
}
