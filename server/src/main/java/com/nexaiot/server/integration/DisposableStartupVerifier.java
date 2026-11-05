package com.nexaiot.server.integration;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.integration.mqtt.core.Mqttv3ClientManager;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.core.MessageProducer;
import org.springframework.stereotype.Component;

import com.nexaiot.server.mqtt.management.BackendMqttServicePolicy;

@Component
@Profile("b46-disposable")
public final class DisposableStartupVerifier implements CommandLineRunner {

    private final MongoTemplate mongoTemplate;
    private final Mqttv3ClientManager mqttClientManager;
    private final String mongoUri;
    private final String mqttBrokerUrl;
    private final String mqttClientId;
    private final String mqttUsername;
    private final String mqttPassword;
    private final boolean mqttAuthRequired;
    private final boolean mqttTlsRequired;
    private final String mqttCaCertificate;
    @SuppressWarnings("unused")
    private final boolean managementEnabled;
    private final String escrowKey;
    private final String escrowKeyVersion;
    private final boolean httpsEnabled;
    private final String httpsKeyStore;
    private final String httpsKeyStorePassword;
    private final List<MessageProducer> mqttInboundProducers;
    private final String applicationMode;
    private final String expectedMongoHost;
    private final int expectedMongoPort;
    private final String expectedMqttHost;
    private final int expectedMqttPort;

    public DisposableStartupVerifier(
            MongoTemplate mongoTemplate,
            Mqttv3ClientManager mqttClientManager,
            @Value("${spring.mongodb.uri}") String mongoUri,
            @Value("${mqtt.broker-url}") String mqttBrokerUrl,
            @Value("${mqtt.client-id}") String mqttClientId,
            @Value("${mqtt.username}") String mqttUsername,
            @Value("${mqtt.password}") String mqttPassword,
            @Value("${mqtt.auth-required}") boolean mqttAuthRequired,
            @Value("${mqtt.tls-required}") boolean mqttTlsRequired,
            @Value("${mqtt.ca-certificate}") String mqttCaCertificate,
            @Value("${mqtt.management.enabled}") boolean managementEnabled,
            @Value("${provisioning.credential-escrow.key}") String escrowKey,
            @Value("${provisioning.credential-escrow.key-version}") String escrowKeyVersion,
            @Value("${server.ssl.enabled}") boolean httpsEnabled,
            @Value("${server.ssl.key-store}") String httpsKeyStore,
            @Value("${server.ssl.key-store-password}") String httpsKeyStorePassword,
            @Value("${nexaiot.b46.application-mode}") String applicationMode,
            @Value("${nexaiot.b46.expected-mongo-host}") String expectedMongoHost,
            @Value("${nexaiot.b46.expected-mongo-port}") int expectedMongoPort,
            @Value("${nexaiot.b46.expected-mqtt-host}") String expectedMqttHost,
            @Value("${nexaiot.b46.expected-mqtt-port}") int expectedMqttPort,
            List<MessageProducer> mqttInboundProducers
    ) {
        this.mongoTemplate = mongoTemplate;
        this.mqttClientManager = mqttClientManager;
        this.mongoUri = mongoUri;
        this.mqttBrokerUrl = mqttBrokerUrl;
        this.mqttClientId = mqttClientId;
        this.mqttUsername = mqttUsername;
        this.mqttPassword = mqttPassword;
        this.mqttAuthRequired = mqttAuthRequired;
        this.mqttTlsRequired = mqttTlsRequired;
        this.mqttCaCertificate = mqttCaCertificate;
        this.managementEnabled = managementEnabled;
        this.escrowKey = escrowKey;
        this.escrowKeyVersion = escrowKeyVersion;
        this.httpsEnabled = httpsEnabled;
        this.httpsKeyStore = httpsKeyStore;
        this.httpsKeyStorePassword = httpsKeyStorePassword;
        this.applicationMode = applicationMode;
        this.expectedMongoHost = expectedMongoHost;
        this.expectedMongoPort = expectedMongoPort;
        this.expectedMqttHost = expectedMqttHost;
        this.expectedMqttPort = expectedMqttPort;
        this.mqttInboundProducers = mqttInboundProducers;
    }

    @Override
    public void run(String... args) {
        validateConfiguration();
        verifyMongo();
        waitForMqtt();
        verifyMqttSubscriptions();
    }

    private void validateConfiguration() {
        URI mongo = parse(mongoUri, "MongoDB URI");
        String expectedMongoModeHost = "host".equals(applicationMode) ? "127.0.0.1" : "mongo";
        String expectedMqttModeHost = "host".equals(applicationMode) ? "127.0.0.1" : "mosquitto";
        if (!("host".equals(applicationMode) || "container".equals(applicationMode))
                || !"mongodb".equalsIgnoreCase(mongo.getScheme())
                || !expectedMongoModeHost.equalsIgnoreCase(mongo.getHost())
                || !expectedMongoHost.equalsIgnoreCase(mongo.getHost())
                || mongo.getPort() != expectedMongoPort
                || !"/nexaiot_b46".equals(mongo.getPath())) {
            throw new IllegalStateException("Disposable startup requires the disposable MongoDB endpoint.");
        }

        URI mqtt = parse(mqttBrokerUrl, "MQTT broker URL");
        if (!"ssl".equalsIgnoreCase(mqtt.getScheme())
                || !expectedMqttModeHost.equalsIgnoreCase(mqtt.getHost())
                || !expectedMqttHost.equalsIgnoreCase(mqtt.getHost())
                || mqtt.getPort() != expectedMqttPort) {
            throw new IllegalStateException("Disposable startup requires verified TLS MQTT on Mosquitto.");
        }
        if (!BackendMqttServicePolicy.CLIENT_ID.equals(mqttClientId)
                || !BackendMqttServicePolicy.USERNAME.equals(mqttUsername)
                || mqttPassword == null || mqttPassword.isBlank()
                || !mqttAuthRequired || !mqttTlsRequired) {
            throw new IllegalStateException("Disposable startup requires the restricted backend MQTT identity.");
        }

        requireFile(mqttCaCertificate, "MQTT CA certificate");
        requireFile(httpsKeyStore, "HTTPS key store");
        if (!httpsEnabled || httpsKeyStorePassword == null || httpsKeyStorePassword.isBlank()) {
            throw new IllegalStateException("Disposable startup requires HTTPS with a test key store.");
        }
        if (escrowKeyVersion == null || escrowKeyVersion.isBlank() || escrowKey == null) {
            throw new IllegalStateException("Disposable startup requires an external escrow key.");
        }
        try {
            if (Base64.getDecoder().decode(escrowKey).length != 32) {
                throw new IllegalStateException("Disposable escrow key must be 256 bits.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Disposable escrow key is invalid.", exception);
        }
    }

    private void verifyMongo() {
        Document response = mongoTemplate.getDb().runCommand(new Document("ping", 1));
        Object ok = response.get("ok");
        if (!(ok instanceof Number number) || number.doubleValue() != 1.0d) {
            throw new IllegalStateException("Disposable MongoDB ping failed.");
        }
    }

    private void waitForMqtt() {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (!mqttClientManager.isConnected() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Disposable MQTT verification was interrupted.", exception);
            }
        }
        if (!mqttClientManager.isConnected()) {
            throw new IllegalStateException("Disposable backend MQTT identity did not authenticate.");
        }
    }

    private void verifyMqttSubscriptions() {
        Set<String> expected = Set.of(
                BackendMqttServicePolicy.TELEMETRY_TOPIC,
                BackendMqttServicePolicy.STATE_TOPIC,
                BackendMqttServicePolicy.COMMAND_RESULT_TOPIC,
                BackendMqttServicePolicy.ACTIVATION_RESPONSE_TOPIC);
        List<MqttPahoMessageDrivenChannelAdapter> adapters = mqttInboundProducers.stream()
                .filter(MqttPahoMessageDrivenChannelAdapter.class::isInstance)
                .map(MqttPahoMessageDrivenChannelAdapter.class::cast)
                .toList();
        Set<String> actual = adapters.stream()
                .filter(MqttPahoMessageDrivenChannelAdapter::isRunning)
                .flatMap(adapter -> Arrays.stream(adapter.getTopic()))
                .collect(java.util.stream.Collectors.toSet());
        if (adapters.size() != expected.size() || !actual.equals(expected)) {
            throw new IllegalStateException("Disposable MQTT subscriptions are incomplete or unexpected.");
        }
    }

    private URI parse(String value, String label) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(label + " is invalid.", exception);
        }
    }

    private void requireFile(String value, String label) {
        if (value == null || value.isBlank() || !Files.isRegularFile(Path.of(value))) {
            throw new IllegalStateException(label + " is unavailable.");
        }
    }
}
