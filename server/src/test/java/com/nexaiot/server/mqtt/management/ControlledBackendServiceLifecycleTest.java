package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

@EnabledIfSystemProperty(named = "nexaiot.integration.backend-lifecycle", matches = "true")
class ControlledBackendServiceLifecycleTest {

    private static final String TARGET = "ssl://localhost:8884";

    @Test
    void reconnectsWithSavedCredentialThenRejectsAfterRevocation() throws Exception {
        requireAuthorization();
        String brokerUrl = requiredEnvironment("MQTT_MANAGEMENT_BROKER_URL");
        String caCertificate = requiredEnvironment("MQTT_MANAGEMENT_CA_CERTIFICATE");
        String managementPassword = requiredEnvironment("MQTT_MANAGEMENT_PASSWORD");
        String backendPassword = requiredEnvironment("MQTT_BACKEND_PASSWORD");
        assertEquals(TARGET, brokerUrl);
        assertTrue(Files.isRegularFile(Path.of(caCertificate)));

        MqttAsyncClient client = connect(brokerUrl, caCertificate, backendPassword);
        client.disconnect().waitForCompletion(5000);
        client.close();

        MosquittoDynamicSecurityClient broker = new MosquittoDynamicSecurityClient(
                new ObjectMapper(), true, brokerUrl, "nexaiot-provisioning-management",
                "admin", managementPassword, true, 5, caCertificate);
        broker.revokeDeviceIdentity(BackendMqttServicePolicy.USERNAME);

        MqttAsyncClient revoked = new MqttAsyncClient(brokerUrl, BackendMqttServicePolicy.CLIENT_ID);
        try {
            assertThrows(MqttException.class, () -> {
                MqttConnectOptions options = options(caCertificate, backendPassword);
                revoked.connect(options).waitForCompletion(5000);
            });
        } finally {
            if (revoked.isConnected()) {
                revoked.disconnect().waitForCompletion(5000);
            }
            revoked.close();
        }
    }

    private MqttAsyncClient connect(String brokerUrl, String caCertificate, String password)
            throws Exception {
        MqttAsyncClient client = new MqttAsyncClient(brokerUrl, BackendMqttServicePolicy.CLIENT_ID);
        client.connect(options(caCertificate, password)).waitForCompletion(5000);
        return client;
    }

    private MqttConnectOptions options(String caCertificate, String password) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(BackendMqttServicePolicy.USERNAME);
        options.setPassword(password.toCharArray());
        options.setSocketFactory(MqttManagementTls.socketFactory(caCertificate));
        options.setHttpsHostnameVerificationEnabled(true);
        options.setAutomaticReconnect(false);
        options.setCleanSession(true);
        options.setConnectionTimeout(5);
        return options;
    }

    private void requireAuthorization() {
        if (!"true".equalsIgnoreCase(System.getProperty("nexaiot.integration.backend-lifecycle"))) {
            throw new IllegalStateException("Explicit backend lifecycle authorization is required.");
        }
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for backend lifecycle testing.");
        }
        return value;
    }
}
