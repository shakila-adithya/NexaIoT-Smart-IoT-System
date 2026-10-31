package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Explicitly invoked, read-only broker connectivity check. It is excluded
 * from ordinary Maven test execution unless the read-only flag is supplied.
 * This test must never be expanded to include provisioning or cleanup calls.
 */
@EnabledIfSystemProperty(named = "nexaiot.integration.readonly", matches = "true")
class ReadOnlyMosquittoManagementConnectionTest {

    private static final String BROKER_URL = "ssl://localhost:1885";
    private static final String MANAGEMENT_USERNAME = "nexaiot-provisioning-management";
    private static final String MANAGEMENT_CLIENT_ID = "nexaiot-provisioning-management";

    @Test
    void readsManagementIdentityOnly() {
        String password = requiredEnvironment("NEXAIOT_MQTT_MANAGEMENT_PASSWORD");
        String caCertificate = requiredEnvironment("NEXAIOT_MQTT_CA_CERTIFICATE");
        if (!Files.isRegularFile(Path.of(caCertificate))) {
            throw new IllegalStateException(
                    "NEXAIOT_MQTT_CA_CERTIFICATE must identify a readable certificate file.");
        }

        MosquittoDynamicSecurityClient broker = new MosquittoDynamicSecurityClient(
                new ObjectMapper(), true, BROKER_URL, MANAGEMENT_CLIENT_ID,
                MANAGEMENT_USERNAME, password, true, 5, caCertificate);

        BrokerDeviceIdentityStatus identity =
                broker.inspectEnrollmentStatus(MANAGEMENT_USERNAME);

        assertEquals(MANAGEMENT_USERNAME, identity.username());
        assertEquals(MANAGEMENT_CLIENT_ID, identity.clientId());
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for this read-only test.");
        }
        return value;
    }
}
