package com.nexaiot.server.mqtt.management;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexaiot.server.mqtt.MqttConfigurationValidator;
import com.nexaiot.server.mqtt.MqttTopicSecurity;

/**
 * Small, lazy Mosquitto Dynamic Security adapter.
 *
 * It never connects at application startup. Each operation uses the separate
 * management identity and is serialized to prevent client-id collisions.
 */
@Service
public class MosquittoDynamicSecurityClient implements BrokerManagementClient {

    private static final String CONTROL_TOPIC = "$CONTROL/dynamic-security/v1";
    private static final String RESPONSE_TOPIC = CONTROL_TOPIC + "/response";
    private static final int QOS = 1;

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String brokerUrl;
    private final String clientId;
    private final String username;
    private final String password;
    private final boolean tlsRequired;
    private final long timeoutMillis;

    public MosquittoDynamicSecurityClient(
            ObjectMapper objectMapper,
            @Value("${mqtt.management.enabled:false}") boolean enabled,
            @Value("${mqtt.management.broker-url:tcp://127.0.0.1:1884}") String brokerUrl,
            @Value("${mqtt.management.client-id:nexaiot-provisioning-management}") String clientId,
            @Value("${mqtt.management.username:}") String username,
            @Value("${mqtt.management.password:}") String password,
            @Value("${mqtt.management.tls-required:true}") boolean tlsRequired,
            @Value("${mqtt.management.timeout-seconds:5}") long timeoutSeconds
    ) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("MQTT management timeout must be positive.");
        }
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.username = username;
        this.password = password;
        this.tlsRequired = tlsRequired;
        this.timeoutMillis = TimeUnit.SECONDS.toMillis(timeoutSeconds);
    }

    @Override
    public BrokerDeviceIdentity createDeviceIdentity(
            String deviceUsername,
            String deviceClientId,
            String devicePassword
    ) {
        requireEnabled();
        requireValue(deviceUsername, "Device username");
        requireValue(deviceClientId, "Device client ID");
        requireValue(devicePassword, "Device password");

        ObjectNode command = command("createClient");
        command.put("username", deviceUsername);
        command.put("password", devicePassword);
        command.put("clientid", deviceClientId);
        execute(List.of(command));
        return new BrokerDeviceIdentity(deviceUsername, deviceClientId);
    }

    @Override
    public BrokerOperationResult assignDevicePermissions(String deviceUsername, String deviceKey) {
        requireEnabled();
        requireValue(deviceUsername, "Device username");
        MqttTopicSecurity.validateDeviceKey(deviceKey);

        String roleName = roleName(deviceKey);
        List<ObjectNode> commands = new ArrayList<>();

        ObjectNode createRole = command("createRole");
        createRole.put("rolename", roleName);
        commands.add(createRole);

        commands.add(acl(roleName, "publishClientSend",
                telemetryTopic(deviceKey), "allow", 100));
        commands.add(acl(roleName, "publishClientSend",
                stateTopic(deviceKey), "allow", 100));
        commands.add(acl(roleName, "subscribeLiteral",
                MqttTopicSecurity.commandTopic(deviceKey), "allow", 100));
        commands.add(acl(roleName, "publishClientReceive",
                MqttTopicSecurity.commandTopic(deviceKey), "allow", 100));

        // Publish denies are safe because the exact publish allows have higher
        // priority. MQTT wildcard matching does not include $-topics, so those
        // control namespaces are denied explicitly as well.
        commands.add(acl(roleName, "publishClientSend", "#", "deny", 1));
        commands.add(acl(roleName, "publishClientReceive", "#", "deny", 1));
        commands.add(acl(roleName, "publishClientSend",
                "$CONTROL/dynamic-security/#", "deny", 1));
        commands.add(acl(roleName, "publishClientReceive",
                "$CONTROL/dynamic-security/#", "deny", 1));
        commands.add(acl(roleName, "publishClientSend", "$SYS/#", "deny", 1));
        commands.add(acl(roleName, "publishClientReceive", "$SYS/#", "deny", 1));

        // Do not add subscribePattern # here. Mosquitto evaluates pattern
        // subscriptions separately from literal filters; a blanket pattern
        // deny can also reject the intended exact command subscription. The
        // broker's subscribe default must remain deny, as documented by
        // Mosquitto Dynamic Security.

        ObjectNode addRole = command("addClientRole");
        addRole.put("username", deviceUsername);
        addRole.put("rolename", roleName);
        addRole.put("priority", 100);
        commands.add(addRole);

        execute(commands);
        return new BrokerOperationResult("assignDevicePermissions", deviceKey, true);
    }

    @Override
    public BrokerDeviceIdentityStatus verifyDeviceIdentity(String deviceUsername) {
        return inspectEnrollmentStatus(deviceUsername);
    }

    @Override
    public BrokerOperationResult disableDeviceIdentity(String deviceUsername) {
        requireEnabled();
        requireValue(deviceUsername, "Device username");
        ObjectNode command = command("disableClient");
        command.put("username", deviceUsername);
        execute(List.of(command));
        return new BrokerOperationResult("disableDeviceIdentity", deviceUsername, true);
    }

    @Override
    public BrokerOperationResult revokeDeviceIdentity(String deviceUsername) {
        requireEnabled();
        requireValue(deviceUsername, "Device username");
        ObjectNode command = command("deleteClient");
        command.put("username", deviceUsername);
        execute(List.of(command));
        return new BrokerOperationResult("revokeDeviceIdentity", deviceUsername, true);
    }

    @Override
    public BrokerDeviceIdentityStatus inspectEnrollmentStatus(String deviceUsername) {
        requireEnabled();
        requireValue(deviceUsername, "Device username");
        ObjectNode command = command("getClient");
        command.put("username", deviceUsername);
        JsonNode response = execute(List.of(command)).get(0);
        JsonNode client = response.path("data").path("client");
        if (!client.isObject()) {
            throw new BrokerManagementException(
                    BrokerManagementFailure.INVALID_RESPONSE,
                    "Broker returned no client identity data.");
        }
        return new BrokerDeviceIdentityStatus(
                client.path("username").asText(deviceUsername),
                client.path("clientid").asText(null),
                client.path("disabled").asBoolean(false),
                true);
    }

    private synchronized List<JsonNode> execute(List<ObjectNode> commands) {
        requireEnabled();
        validateManagementConfiguration();

        Map<String, String> expectedCommands = new java.util.LinkedHashMap<>();
        CountDownLatch responseLatch = new CountDownLatch(commands.size());
        AtomicReference<BrokerManagementException> responseError = new AtomicReference<>();
        MqttAsyncClient client = null;

        try {
            for (ObjectNode command : commands) {
                String correlation = UUID.randomUUID().toString();
                command.put("correlationData", correlation);
                expectedCommands.put(correlation, command.path("command").asText());
            }
            DynamicSecurityResponseProcessor responseProcessor =
                    new DynamicSecurityResponseProcessor(objectMapper, expectedCommands);

            client = new MqttAsyncClient(brokerUrl, clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setServerURIs(new String[]{brokerUrl});
            options.setCleanSession(true);
            options.setAutomaticReconnect(false);
            options.setConnectionTimeout((int) TimeUnit.MILLISECONDS.toSeconds(timeoutMillis));
            options.setUserName(username);
            options.setPassword(password.toCharArray());

            IMqttMessageListener listener = (topic, message) -> {
                try {
                    int responseCount = responseProcessor.accept(message.getPayload());
                    while (responseLatch.getCount() > responseCount) {
                        responseLatch.countDown();
                    }
                } catch (BrokerManagementException exception) {
                    responseError.compareAndSet(null, exception);
                    responseLatch.countDown();
                }
            };

            client.connect(options).waitForCompletion(timeoutMillis);
            client.subscribe(RESPONSE_TOPIC, QOS, listener)
                    .waitForCompletion(timeoutMillis);

            ObjectNode request = objectMapper.createObjectNode();
            ArrayNode requestCommands = request.putArray("commands");
            for (ObjectNode command : commands) {
                requestCommands.add(command);
            }
            MqttMessage message = new MqttMessage(
                    objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            message.setQos(QOS);
            client.publish(CONTROL_TOPIC, message).waitForCompletion(timeoutMillis);

            BrokerManagementException observedError = responseError.get();
            if (observedError != null) {
                throw observedError;
            }
            if (!responseLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new BrokerManagementException(
                        BrokerManagementFailure.TIMEOUT,
                        "Dynamic Security response timed out.");
            }

            observedError = responseError.get();
            if (observedError != null) {
                throw observedError;
            }

            return responseProcessor.complete();
        } catch (BrokerManagementException exception) {
            throw exception;
        } catch (MqttException exception) {
            BrokerManagementFailure failure = exception.getReasonCode() == 4
                    ? BrokerManagementFailure.AUTHENTICATION_FAILED
                    : BrokerManagementFailure.CONNECTION_FAILED;
            throw new BrokerManagementException(failure,
                    "Dynamic Security broker operation failed.", exception);
        } catch (Exception exception) {
            throw new BrokerManagementException(
                    BrokerManagementFailure.UNKNOWN_STATE,
                    "Dynamic Security broker operation did not complete.", exception);
        } finally {
            if (client != null) {
                try {
                    if (client.isConnected()) {
                        client.disconnect().waitForCompletion(timeoutMillis);
                    }
                } catch (Exception ignored) {
                    // Cleanup must not hide the operation result.
                }
                try {
                    client.close();
                } catch (MqttException ignored) {
                    // Cleanup must not hide the operation result.
                }
            }
        }
    }

    private ObjectNode command(String name) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("command", name);
        return command;
    }

    private ObjectNode acl(
            String roleName,
            String aclType,
            String topic,
            String access,
            int priority
    ) {
        ObjectNode command = command("addRoleACL");
        command.put("rolename", roleName);
        command.put("acltype", aclType);
        command.put("topic", topic);
        command.put("allow", "allow".equals(access));
        command.put("priority", priority);
        return command;
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new BrokerManagementException(
                    BrokerManagementFailure.DISABLED,
                    "MQTT broker management is disabled.");
        }
    }

    private void validateManagementConfiguration() {
        try {
            MqttConfigurationValidator.validate(
                    brokerUrl, username, password, true, tlsRequired);
            URI uri = URI.create(brokerUrl.trim());
            if (!MqttConfigurationValidator.usesTls(brokerUrl)
                    && !isLoopbackHost(uri.getHost())) {
                throw new IllegalArgumentException(
                        "Remote MQTT management requires TLS.");
            }
        } catch (IllegalArgumentException exception) {
            throw new BrokerManagementException(
                    BrokerManagementFailure.INVALID_CONFIGURATION,
                    "MQTT management configuration is invalid.", exception);
        }
    }

    private void requireValue(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
    }

    private String roleName(String deviceKey) {
        return "nexaiot-device-" + deviceKey;
    }

    private String telemetryTopic(String deviceKey) {
        return "nexaiot/devices/" + deviceKey + "/telemetry";
    }

    private String stateTopic(String deviceKey) {
        return "nexaiot/devices/" + deviceKey + "/state";
    }

    private boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
    }
}
