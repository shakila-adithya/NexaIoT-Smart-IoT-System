package com.nexaiot.server.mqtt.management;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Offline-testable parser and validator for Mosquitto Dynamic Security responses.
 * It accepts the documented response envelope: {"responses": [...] }.
 */
final class DynamicSecurityResponseProcessor {

    private final ObjectMapper objectMapper;
    private final Map<String, String> expectedCommands;
    private final Map<String, JsonNode> responses = new LinkedHashMap<>();

    DynamicSecurityResponseProcessor(ObjectMapper objectMapper, Map<String, String> expectedCommands) {
        this.objectMapper = objectMapper;
        this.expectedCommands = Collections.unmodifiableMap(new LinkedHashMap<>(expectedCommands));
    }

    int accept(byte[] payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.path("responses").isArray()) {
                throw invalid("Dynamic Security response array is missing.");
            }
            for (JsonNode response : root.path("responses")) {
                String correlation = response.path("correlationData").asText(null);
                String expectedCommand = correlation == null ? null : expectedCommands.get(correlation);
                if (expectedCommand == null) {
                    throw invalid("Unexpected Dynamic Security response correlation.");
                }
                String actualCommand = response.path("command").asText(null);
                if (!expectedCommand.equals(actualCommand)) {
                    throw invalid("Dynamic Security response command mismatch.");
                }
                if (responses.putIfAbsent(correlation, response) == null) {
                    validateBrokerError(response);
                }
            }
            return responses.size();
        } catch (BrokerManagementException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("Dynamic Security response was malformed.", exception);
        }
    }

    List<JsonNode> complete() {
        if (responses.size() != expectedCommands.size()) {
            throw invalid("Dynamic Security response was incomplete.");
        }
        List<JsonNode> ordered = new ArrayList<>();
        expectedCommands.keySet().forEach(correlation -> ordered.add(responses.get(correlation)));
        return ordered;
    }

    private void validateBrokerError(JsonNode response) {
        JsonNode error = response.get("error");
        if (error == null || error.isNull() || error.asText().isBlank()) {
            return;
        }
        String errorText = error.asText();
        String normalized = errorText.toLowerCase();
        BrokerManagementFailure failure;
        if (normalized.contains("already exists")) {
            failure = response.path("command").asText().contains("Role")
                    ? BrokerManagementFailure.DUPLICATE_ROLE
                    : BrokerManagementFailure.DUPLICATE_CLIENT;
        } else if (normalized.contains("not authorised")
                || normalized.contains("not authorized")
                || normalized.contains("unauthori")) {
            failure = BrokerManagementFailure.UNAUTHORIZED;
        } else {
            failure = BrokerManagementFailure.COMMAND_REJECTED;
        }
        throw new BrokerManagementException(failure, "Dynamic Security command was rejected.");
    }

    private BrokerManagementException invalid(String message) {
        return new BrokerManagementException(BrokerManagementFailure.INVALID_RESPONSE, message);
    }

    private BrokerManagementException invalid(String message, Throwable cause) {
        return new BrokerManagementException(BrokerManagementFailure.INVALID_RESPONSE, message, cause);
    }
}
