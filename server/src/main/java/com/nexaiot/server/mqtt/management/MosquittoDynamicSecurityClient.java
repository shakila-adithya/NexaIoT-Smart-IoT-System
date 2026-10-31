package com.nexaiot.server.mqtt.management;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.SocketFactory;

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
    private final String caCertificatePath;

    public MosquittoDynamicSecurityClient(
            ObjectMapper objectMapper,
            @Value("${mqtt.management.enabled:false}") boolean enabled,
            @Value("${mqtt.management.broker-url:tcp://127.0.0.1:1884}") String brokerUrl,
            @Value("${mqtt.management.client-id:nexaiot-provisioning-management}") String clientId,
            @Value("${mqtt.management.username:}") String username,
            @Value("${mqtt.management.password:}") String password,
            @Value("${mqtt.management.tls-required:true}") boolean tlsRequired,
            @Value("${mqtt.management.timeout-seconds:5}") long timeoutSeconds,
            @Value("${mqtt.management.ca-certificate:}") String caCertificatePath
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
        this.caCertificatePath = caCertificatePath;
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

        createDeviceRolePermissions(deviceKey);
        assignDeviceRole(deviceUsername, deviceKey);
        return new BrokerOperationResult("assignDevicePermissions", deviceKey, true);
    }

    @Override
    public BrokerOperationResult createDeviceRolePermissions(String deviceKey) {
        requireEnabled();
        MqttTopicSecurity.validateDeviceKey(deviceKey);

        String roleName = roleName(deviceKey);
        execute(rolePermissionCommands(roleName, deviceKey));
        return new BrokerOperationResult("createDeviceRolePermissions", deviceKey, true);
    }

    public BrokerDeviceIdentity createBackendServiceIdentity(
            String backendClientId,
            String backendPassword
    ) {
        requireEnabled();
        requireValue(backendClientId, "Backend client ID");
        requireValue(backendPassword, "Backend password");
        ObjectNode command = command("createClient");
        command.put("username", BackendMqttServicePolicy.USERNAME);
        command.put("password", backendPassword);
        command.put("clientid", backendClientId);
        execute(List.of(command));
        return new BrokerDeviceIdentity(BackendMqttServicePolicy.USERNAME, backendClientId);
    }

    public BrokerOperationResult createBackendServiceRolePermissions() {
        requireEnabled();
        execute(BackendMqttServicePolicy.roleCommands(objectMapper));
        return new BrokerOperationResult(
                "createBackendServiceRolePermissions",
                BackendMqttServicePolicy.ROLE_NAME,
                true);
    }

    public BrokerOperationResult assignBackendServiceRole() {
        requireEnabled();
        ObjectNode command = command("addClientRole");
        command.put("username", BackendMqttServicePolicy.USERNAME);
        command.put("rolename", BackendMqttServicePolicy.ROLE_NAME);
        command.put("priority", 100);
        execute(List.of(command));
        return new BrokerOperationResult(
                "assignBackendServiceRole",
                BackendMqttServicePolicy.USERNAME,
                true);
    }

    public BrokerOperationResult rotateClientPassword(String clientUsername, String newPassword) {
        requireEnabled();
        requireValue(clientUsername, "Client username");
        requireValue(newPassword, "New client password");
        execute(List.of(setClientPasswordCommand(objectMapper, clientUsername, newPassword)));
        return new BrokerOperationResult("rotateClientPassword", clientUsername, true);
    }

    static ObjectNode setClientPasswordCommand(
            ObjectMapper mapper,
            String clientUsername,
            String newPassword
    ) {
        ObjectNode command = mapper.createObjectNode();
        command.put("command", "setClientPassword");
        command.put("username", clientUsername);
        command.put("password", newPassword);
        return command;
    }

    @Override
    public BrokerOperationResult assignDeviceRole(String deviceUsername, String deviceKey) {
        requireEnabled();
        requireValue(deviceUsername, "Device username");
        MqttTopicSecurity.validateDeviceKey(deviceKey);

        ObjectNode addRole = command("addClientRole");
        addRole.put("username", deviceUsername);
        addRole.put("rolename", roleName(deviceKey));
        addRole.put("priority", 100);
        execute(List.of(addRole));
        return new BrokerOperationResult("assignDeviceRole", deviceKey, true);
    }

    private List<ObjectNode> rolePermissionCommands(String roleName, String deviceKey) {
        List<ObjectNode> commands = new ArrayList<>();
        ObjectNode createRole = command("createRole");
        createRole.put("rolename", roleName);
        commands.add(createRole);

        commands.add(acl(roleName, "publishClientSend",
                telemetryTopic(deviceKey), "allow", 100));
        commands.add(acl(roleName, "publishClientSend",
                stateTopic(deviceKey), "allow", 100));
        commands.add(acl(roleName, "publishClientSend",
                commandResultTopic(deviceKey), "allow", 100));
        commands.add(acl(roleName, "subscribeLiteral",
                MqttTopicSecurity.commandTopic(deviceKey), "allow", 100));
        commands.add(acl(roleName, "publishClientReceive",
                MqttTopicSecurity.commandTopic(deviceKey), "allow", 100));
        commands.add(acl(roleName, "publishClientSend",
                MqttTopicSecurity.activationResponseTopic(deviceKey), "allow", 100));
        commands.add(acl(roleName, "subscribeLiteral",
                MqttTopicSecurity.activationChallengeTopic(deviceKey), "allow", 100));
        commands.add(acl(roleName, "publishClientReceive",
                MqttTopicSecurity.activationChallengeTopic(deviceKey), "allow", 100));

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

        return commands;
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
    public BrokerRoleStatus inspectRole(String roleName) {
        requireEnabled();
        requireValue(roleName, "Role name");
        ObjectNode command = command("getRole");
        command.put("rolename", roleName);
        JsonNode response = execute(List.of(command)).get(0);
        JsonNode role = response.path("data").path("role");
        if (!role.isObject()) {
            throw new BrokerManagementException(
                    BrokerManagementFailure.INVALID_RESPONSE,
                    "Broker returned no role data.");
        }
        ObjectNode listClients = command("listClients");
        listClients.put("verbose", true);
        listClients.put("count", -1);
        listClients.put("offset", 0);
        JsonNode clientData = execute(List.of(listClients)).get(0).path("data");

        ObjectNode listGroups = command("listGroups");
        listGroups.put("verbose", true);
        listGroups.put("count", -1);
        listGroups.put("offset", 0);
        JsonNode groupData = execute(List.of(listGroups)).get(0).path("data");
        return BrokerRoleReferenceParser.parse(roleName, clientData, groupData);
    }

    public JsonNode inspectRolePermissions(String roleName) {
        requireEnabled();
        requireValue(roleName, "Role name");
        ObjectNode command = command("getRole");
        command.put("rolename", roleName);
        JsonNode role = execute(List.of(command)).get(0).path("data").path("role");
        if (!role.isObject() || !role.path("acls").isArray()) {
            throw new BrokerManagementException(
                    BrokerManagementFailure.INVALID_RESPONSE,
                    "Broker returned incomplete role permissions.");
        }
        return role;
    }

    @Override
    public BrokerDeviceProvisioningStatus verifyDeviceProvisioning(
            String deviceUsername,
            String deviceKey
    ) {
        requireEnabled();
        requireValue(deviceUsername, "Device username");
        MqttTopicSecurity.validateDeviceKey(deviceKey);
        ObjectNode getClient = command("getClient");
        getClient.put("username", deviceUsername);
        ObjectNode getRole = command("getRole");
        getRole.put("rolename", roleName(deviceKey));
        List<JsonNode> responses = execute(List.of(getClient, getRole));
        return BrokerProvisioningVerifier.verify(responses.get(0), responses.get(1), deviceKey);
    }

    @Override
    public BrokerOperationResult deleteRole(String roleName) {
        requireEnabled();
        requireValue(roleName, "Role name");
        ObjectNode command = command("deleteRole");
        command.put("rolename", roleName);
        execute(List.of(command));
        return new BrokerOperationResult("deleteRole", roleName, true);
    }

    @Override
    public BrokerDeviceIdentityStatus inspectEnrollmentStatus(String deviceUsername) {
        requireEnabled();
        requireValue(deviceUsername, "Device username");
        BrokerClientInspection inspection = inspectClient(deviceUsername);
        if (!inspection.clientIdPresent()) {
            throw new BrokerManagementException(
                    BrokerManagementFailure.INVALID_RESPONSE,
                    "Broker returned an incomplete client identity.");
        }
        return new BrokerDeviceIdentityStatus(
                inspection.username(), inspection.clientId(), inspection.disabled(),
                inspection.acknowledged());
    }

    public BrokerClientInspection inspectClient(String clientUsername) {
        requireEnabled();
        requireValue(clientUsername, "Client username");
        ObjectNode command = command("getClient");
        command.put("username", clientUsername);
        JsonNode response = execute(List.of(command)).get(0);
        return parseClientInspection(response, clientUsername);
    }

    static BrokerClientInspection parseClientInspection(JsonNode response, String expectedUsername) {
        JsonNode client = response.path("data").path("client");
        if (!client.isObject()) {
            throw new BrokerManagementException(
                    BrokerManagementFailure.INVALID_RESPONSE,
                    "Broker returned no client identity data.");
        }
        if (!client.path("username").isTextual()
                || client.path("username").asText().isBlank()
                || !expectedUsername.equals(client.path("username").asText())) {
            throw new BrokerManagementException(
                    BrokerManagementFailure.INVALID_RESPONSE,
                    "Broker returned an incomplete client identity.");
        }
        JsonNode clientId = client.get("clientid");
        boolean clientIdPresent = clientId != null;
        if (clientIdPresent && (!clientId.isTextual() || clientId.asText().isBlank())) {
            throw new BrokerManagementException(
                    BrokerManagementFailure.INVALID_RESPONSE,
                    "Broker returned a malformed client ID.");
        }
        JsonNode disabled = client.get("disabled");
        if (disabled != null && !disabled.isBoolean()) {
            throw new BrokerManagementException(
                    BrokerManagementFailure.INVALID_RESPONSE,
                    "Broker returned a malformed disabled flag.");
        }
        return new BrokerClientInspection(
                client.path("username").asText(),
                clientIdPresent ? clientId.asText() : null,
                clientIdPresent,
                disabled != null && disabled.asBoolean(),
                true);
    }

    private synchronized List<JsonNode> execute(List<ObjectNode> commands) {
        requireEnabled();
        validateManagementConfiguration();

        Map<String, String> expectedCommands = new java.util.LinkedHashMap<>();
        // One latch represents completion of the response batch. The processor
        // owns unique-response counting, so duplicate responses cannot affect it.
        CountDownLatch responseLatch = new CountDownLatch(1);
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
            // Match mosquitto_ctrl's default protocol so broker-side
            // authentication behaviour is identical and deterministic.
            options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
            options.setConnectionTimeout((int) TimeUnit.MILLISECONDS.toSeconds(timeoutMillis));
            options.setUserName(username);
            options.setPassword(password.toCharArray());
            if (MqttConfigurationValidator.usesTls(brokerUrl)) {
                SocketFactory socketFactory = MqttManagementTls.socketFactory(caCertificatePath);
                if (socketFactory != null) {
                    options.setSocketFactory(socketFactory);
                }
                options.setHttpsHostnameVerificationEnabled(true);
            }

            IMqttMessageListener listener = (topic, message) -> {
                try {
                    responseProcessor.accept(message.getPayload());
                    if (responseProcessor.isComplete()) {
                        responseLatch.countDown();
                    }
                } catch (BrokerManagementException exception) {
                    responseError.compareAndSet(null, exception);
                    while (responseLatch.getCount() > 0) {
                        responseLatch.countDown();
                    }
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
            BrokerManagementFailure failure = connectionFailure(exception.getReasonCode());
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

    /**
     * Executes only the broker's read-only Dynamic Security inventory commands.
     * This deliberately has a separate allow-list so a reconciliation test
     * cannot accidentally become a provisioning or cleanup operation.
     */
    synchronized List<JsonNode> executeReadOnlyInventory(List<ObjectNode> commands) {
        Set<String> allowed = Set.of(
                "listClients", "listRoles", "listGroups", "getDefaultACLAccess");
        if (commands == null || commands.isEmpty()) {
            throw new BrokerManagementException(
                    BrokerManagementFailure.INVALID_CONFIGURATION,
                    "At least one read-only inventory command is required.");
        }
        for (ObjectNode command : commands) {
            if (command == null || !allowed.contains(command.path("command").asText())) {
                throw new BrokerManagementException(
                        BrokerManagementFailure.INVALID_CONFIGURATION,
                        "A non-read-only Dynamic Security command was rejected.");
            }
        }
        return execute(commands);
    }

    static BrokerManagementFailure connectionFailure(int reasonCode) {
        return switch (reasonCode) {
            case MqttException.REASON_CODE_CLIENT_EXCEPTION,
                    MqttException.REASON_CODE_FAILED_AUTHENTICATION ->
                    BrokerManagementFailure.AUTHENTICATION_FAILED;
            case MqttException.REASON_CODE_NOT_AUTHORIZED ->
                    BrokerManagementFailure.UNAUTHORIZED;
            default -> BrokerManagementFailure.CONNECTION_FAILED;
        };
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

    private String commandResultTopic(String deviceKey) {
        return "nexaiot/devices/" + deviceKey + "/command-results";
    }

    private boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
    }
}
