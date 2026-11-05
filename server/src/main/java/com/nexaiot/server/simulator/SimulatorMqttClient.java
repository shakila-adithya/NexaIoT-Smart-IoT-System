package com.nexaiot.server.simulator;

import java.util.Arrays;
import java.util.function.BiConsumer;

import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.mqtt.management.GeneratedDeviceCredential;

/**
 * Explicitly-started simulator client. It contains no Spring startup hook and
 * therefore cannot connect merely because the application starts.
 */
public final class SimulatorMqttClient implements AutoCloseable {

    private final SimulatorDeviceIdentity identity;
    private final SimulatorMqttConfiguration configuration;
    private final SimulatorBootstrapAdapter bootstrapAdapter;
    private final SimulatorMqttConnectionFactory connectionFactory;
    private final SimulatorReconnectPolicy reconnectPolicy;
    private final int maximumReconnectAttempts;
    private final SimulatorLifecycle lifecycle;
    private final SimulatorActivationEvidenceVerifier activationVerifier;
    private final CapabilityManifest manifest;
    private final BiConsumer<String, byte[]> commandConsumer;
    private SimulatorMqttConnection connection;
    private char[] password;
    private SimulatorConnectionState state = SimulatorConnectionState.BOOTSTRAP_PENDING;
    private SimulatorMqttFailure lastFailure;
    private int reconnectAttempts;
    private boolean stopped;

    public SimulatorMqttClient(
            SimulatorDeviceIdentity identity,
            SimulatorMqttConfiguration configuration,
            SimulatorBootstrapAdapter bootstrapAdapter,
            SimulatorMqttConnectionFactory connectionFactory,
            SimulatorReconnectPolicy reconnectPolicy,
            int maximumReconnectAttempts,
            SimulatorLifecycle lifecycle,
            CapabilityManifest manifest,
            BiConsumer<String, byte[]> commandConsumer,
            SimulatorActivationEvidenceVerifier activationVerifier
    ) {
        if (identity == null || configuration == null || bootstrapAdapter == null
                || connectionFactory == null || reconnectPolicy == null || lifecycle == null
                || commandConsumer == null || activationVerifier == null
                || maximumReconnectAttempts < 0) {
            throw new IllegalArgumentException("Simulator client configuration is incomplete.");
        }
        if (!identity.clientId().equals(configuration.clientId())) {
            throw new IllegalArgumentException("Simulator client id does not match device identity.");
        }
        this.identity = identity;
        this.configuration = configuration;
        this.bootstrapAdapter = bootstrapAdapter;
        this.connectionFactory = connectionFactory;
        this.reconnectPolicy = reconnectPolicy;
        this.maximumReconnectAttempts = maximumReconnectAttempts;
        this.lifecycle = lifecycle;
        this.activationVerifier = activationVerifier;
        this.manifest = manifest;
        this.commandConsumer = commandConsumer;
    }

    public synchronized SimulatorConnectionState state() { return state; }
    public synchronized SimulatorMqttFailure lastFailure() { return lastFailure; }
    public synchronized int reconnectAttempts() { return reconnectAttempts; }

    public synchronized void acceptBootstrap(
            SimulatorBootstrapGrant grant,
            SimulatorDeviceIdentity presentedIdentity,
            GeneratedDeviceCredential credential
    ) {
        requireNotStopped();
        if (state != SimulatorConnectionState.BOOTSTRAP_PENDING) {
            throw new IllegalStateException("Simulator bootstrap is not pending.");
        }
        try (SimulatorCredentialEnvelope envelope = bootstrapAdapter.deliver(
                grant, presentedIdentity, credential)) {
            password = envelope.passwordCopy();
            lifecycle.credentialsDelivered();
            state = SimulatorConnectionState.CREDENTIALS_AVAILABLE;
        } catch (RuntimeException exception) {
            lastFailure = exception instanceof IllegalArgumentException
                    ? SimulatorMqttFailure.BOOTSTRAP_EXPIRED
                    : SimulatorMqttFailure.INVALID_DEVICE_IDENTITY;
            throw exception;
        }
    }

    public synchronized void connect() throws SimulatorMqttException {
        requireNotStopped();
        if (state != SimulatorConnectionState.CREDENTIALS_AVAILABLE
                && state != SimulatorConnectionState.DISCONNECTED
                && state != SimulatorConnectionState.RECONNECTING) {
            throw new SimulatorMqttException(
                    SimulatorMqttFailure.CREDENTIAL_REVOKED,
                    "Simulator is not ready to connect.");
        }
        state = state == SimulatorConnectionState.RECONNECTING
                ? SimulatorConnectionState.RECONNECTING : SimulatorConnectionState.CONNECTING;
        try {
            if (connection == null) {
                connection = connectionFactory.create(identity, configuration,
                        identity.clientId(), identity.clientId(), password.clone(),
                        this::unexpectedDisconnect);
            }
            connection.connect();
            connection.subscribe(SimulatorMqttTopics.commands(identity), commandConsumer);
            if (lifecycle.state() == SimulatorLifecycleState.DISCONNECTED) {
                lifecycle.reconnected();
            } else {
                lifecycle.connected();
            }
            state = SimulatorConnectionState.ACTIVATION_PENDING;
            lastFailure = null;
            reconnectAttempts = 0;
        } catch (SimulatorMqttException exception) {
            lastFailure = exception.failure();
            state = SimulatorConnectionState.FAILED;
            throw exception;
        }
    }

    public synchronized void markActivationVerified(SimulatorActivationEvidence evidence) {
        requireNotStopped();
        if (state != SimulatorConnectionState.ACTIVATION_PENDING) {
            throw new IllegalStateException("Simulator activation evidence is not pending.");
        }
        if (!activationVerifier.verify(identity, evidence)) {
            throw new IllegalArgumentException("Simulator activation evidence was rejected.");
        }
        lifecycle.activationVerified();
        state = SimulatorConnectionState.ACTIVE;
    }

    public synchronized void publishTelemetry(
            SimulatorTelemetry telemetry, byte[] payload, int qos) throws SimulatorMqttException {
        requirePublishable();
        SimulatorCapabilityValidator.validateTelemetry(identity, manifest, telemetry);
        connection.publish(SimulatorMqttTopics.telemetry(identity), payload, qos);
    }

    public synchronized void publishTelemetry(
            SimulatorTelemetry telemetry, SimulatorMessageCodec codec, int qos)
            throws SimulatorMqttException {
        if (codec == null) {
            throw new IllegalArgumentException("Simulator message codec is required.");
        }
        publishTelemetry(telemetry, codec.telemetry(identity, manifest, telemetry), qos);
    }

    public synchronized void publishState(byte[] payload, int qos) throws SimulatorMqttException {
        requirePublishable();
        connection.publish(SimulatorMqttTopics.state(identity), payload, qos);
    }

    public synchronized void publishState(
            SimulatorState simulatorState, SimulatorMessageCodec codec, int qos)
            throws SimulatorMqttException {
        if (codec == null) {
            throw new IllegalArgumentException("Simulator message codec is required.");
        }
        publishState(codec.state(identity, manifest, simulatorState), qos);
    }

    public synchronized void publishCommandResult(
            byte[] payload, int qos) throws SimulatorMqttException {
        requirePublishable();
        connection.publish(SimulatorMqttTopics.commandResults(identity), payload, qos);
    }

    public synchronized void reconnect() throws SimulatorMqttException {
        requireNotStopped();
        if (state != SimulatorConnectionState.DISCONNECTED
                && state != SimulatorConnectionState.FAILED) {
            throw new IllegalStateException("Simulator is not waiting for reconnection.");
        }
        if (reconnectAttempts >= maximumReconnectAttempts) {
            lastFailure = SimulatorMqttFailure.RETRY_EXHAUSTED;
            state = SimulatorConnectionState.FAILED;
            throw new SimulatorMqttException(
                    SimulatorMqttFailure.RETRY_EXHAUSTED,
                    "Simulator reconnect retry limit was reached.");
        }
        reconnectAttempts++;
        state = SimulatorConnectionState.RECONNECTING;
        connect();
    }

    public synchronized java.time.Duration nextReconnectDelay() {
        return reconnectPolicy.delayForAttempt(reconnectAttempts);
    }

    public synchronized void revokeCredentials() throws SimulatorMqttException {
        clearPassword();
        if (connection != null && connection.isConnected()) {
            connection.disconnect();
        }
        state = SimulatorConnectionState.REVOKED;
        lifecycle.revoked();
    }

    public synchronized void shutdown() throws SimulatorMqttException {
        if (stopped) {
            return;
        }
        stopped = true;
        clearPassword();
        if (connection != null) {
            try {
                if (connection.isConnected()) {
                    connection.disconnect();
                }
            } finally {
                connection.close();
            }
        }
        state = SimulatorConnectionState.STOPPED;
    }

    @Override
    public void close() throws SimulatorMqttException {
        shutdown();
    }

    private synchronized void unexpectedDisconnect(SimulatorMqttFailure failure) {
        if (stopped || state == SimulatorConnectionState.REVOKED) {
            return;
        }
        lastFailure = failure == null
                ? SimulatorMqttFailure.UNEXPECTED_DISCONNECT : failure;
        state = SimulatorConnectionState.DISCONNECTED;
        if (lifecycle.state() == SimulatorLifecycleState.ACTIVE
                || lifecycle.state() == SimulatorLifecycleState.CONNECTED) {
            lifecycle.disconnected();
        }
    }

    private void requirePublishable() throws SimulatorMqttException {
        requireNotStopped();
        if (state != SimulatorConnectionState.ACTIVE) {
            throw new SimulatorMqttException(
                    SimulatorMqttFailure.BROKER_UNAVAILABLE,
                    "Simulator is not connected.");
        }
    }

    private void requireNotStopped() {
        if (stopped || state == SimulatorConnectionState.STOPPED
                || state == SimulatorConnectionState.REVOKED) {
            throw new IllegalStateException("Simulator is stopped or revoked.");
        }
    }

    private void clearPassword() {
        if (password != null) {
            Arrays.fill(password, '\0');
            password = null;
        }
    }
}
