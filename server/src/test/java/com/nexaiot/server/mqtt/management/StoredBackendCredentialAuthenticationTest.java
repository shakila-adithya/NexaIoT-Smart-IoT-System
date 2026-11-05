package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "nexaiot.integration.backend-credential-auth", matches = "true")
class StoredBackendCredentialAuthenticationTest {

    private static final String TARGET = "ssl://localhost:8884";

    @Test
    void storedCredentialAuthenticatesWithFixedIdentity() throws Exception {
        String brokerUrl = requiredEnvironment("MQTT_BACKEND_BROKER_URL");
        String caCertificate = requiredEnvironment("MQTT_BACKEND_CA_CERTIFICATE");
        String password = requiredEnvironment("MQTT_BACKEND_PASSWORD");
        assertEquals(TARGET, brokerUrl, "Broker target mismatch");
        assertTrue(Files.isRegularFile(Path.of(caCertificate)), "CA certificate is unavailable");

        MqttAsyncClient client = new MqttAsyncClient(
                brokerUrl, BackendMqttServicePolicy.CLIENT_ID);
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(BackendMqttServicePolicy.USERNAME);
            options.setPassword(password.toCharArray());
            options.setSocketFactory(MqttManagementTls.socketFactory(caCertificate));
            options.setHttpsHostnameVerificationEnabled(true);
            options.setAutomaticReconnect(false);
            options.setCleanSession(true);
            options.setConnectionTimeout(5);
            client.connect(options).waitForCompletion(5000);
            assertTrue(client.isConnected(), "Stored password was not accepted");
            assertFalse(client.getClientId().isBlank(), "Client ID was not set");
            assertEquals(BackendMqttServicePolicy.CLIENT_ID, client.getClientId(),
                    "Fixed client ID mismatch");
        } finally {
            if (client.isConnected()) {
                client.disconnect().waitForCompletion(5000);
            }
            client.close();
        }
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for stored credential authentication.");
        }
        return value;
    }
}
