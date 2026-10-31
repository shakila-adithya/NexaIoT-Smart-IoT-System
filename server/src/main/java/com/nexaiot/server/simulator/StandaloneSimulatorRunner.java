package com.nexaiot.server.simulator;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaiot.server.mqtt.management.DeviceCredentialGenerator;
import com.nexaiot.server.mqtt.management.GeneratedDeviceCredential;

/** Drives one virtual device as a standalone, offline-first process. */
public final class StandaloneSimulatorRunner implements AutoCloseable {

    private final StandaloneSimulatorConfiguration configuration;
    private final SimulatorMessageCodec codec;
    private final SimulatorLifecycle lifecycle;
    private final SimulatorCommandProcessor commandProcessor;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final OfflineSimulatorMqttConnectionFactory offlineFactory;
    private SimulatorMqttClient client;
    private boolean started;
    private double temperature = 27.5;
    private double humidity = 60.0;
    private int battery = 90;
    private int rssi = -52;

    public StandaloneSimulatorRunner(StandaloneSimulatorConfiguration configuration) {
        this(configuration, new OfflineSimulatorMqttConnectionFactory());
    }

    StandaloneSimulatorRunner(StandaloneSimulatorConfiguration configuration,
            OfflineSimulatorMqttConnectionFactory offlineFactory) {
        if (configuration == null || offlineFactory == null) {
            throw new IllegalArgumentException("Standalone simulator configuration is incomplete.");
        }
        this.configuration = configuration;
        this.offlineFactory = offlineFactory;
        this.codec = new SimulatorMessageCodec(new ObjectMapper());
        this.lifecycle = new SimulatorLifecycle();
        this.commandProcessor = new SimulatorCommandProcessor(
                configuration.identity(), configuration.manifest(), codec, Clock.systemUTC());
    }

    public synchronized void start() {
        if (started) {
            throw new IllegalStateException("Standalone simulator is already running.");
        }
        if (configuration.mode() == StandaloneSimulatorConfiguration.Mode.LIVE) {
            throw new IllegalStateException(
                    "Live mode is unavailable until secure onboarding and trusted activation are configured.");
        }
        try {
            lifecycle.pairingStarted();
            lifecycle.pairingClaimed();
            lifecycle.enrollmentAuthorized();
            lifecycle.brokerProvisioned();

            LocalSimulatorBootstrapAdapter bootstrap = new LocalSimulatorBootstrapAdapter(true);
            SimulatorMqttConfiguration mqtt = new SimulatorMqttConfiguration(
                    configuration.brokerUri(), configuration.caCertificate(),
                    configuration.identity().clientId());
            client = new SimulatorMqttClient(configuration.identity(), mqtt, bootstrap,
                    offlineFactory, new SimulatorReconnectPolicy(
                            java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(4)),
                    2, lifecycle, configuration.manifest(), this::handleCommand,
                    new LocalSimulatorActivationEvidenceVerifier(true, "standalone-offline-operation",
                            java.time.Duration.ofMinutes(1)));

            GeneratedDeviceCredential credential = new DeviceCredentialGenerator()
                    .generate(configuration.identity().deviceKey());
            SimulatorBootstrapGrant grant = bootstrap.authorize(configuration.identity(),
                    "offline-owner@example.com", "standalone-offline-operation");
            client.acceptBootstrap(grant, configuration.identity(), credential);
            client.connect();
            client.markActivationVerified(new SimulatorActivationEvidence(
                    configuration.identity().deviceKey(), "standalone-offline-operation",
                    Instant.now(), "offline-demo-proof"));
            started = true;
            System.out.println("Java simulator started in OFFLINE mode; device="
                    + configuration.identity().deviceKey());
            System.out.println("No network connection was opened. Press Ctrl+C to stop.");
            publishTelemetry();
            scheduler.scheduleAtFixedRate(this::safePublishTelemetry,
                    configuration.telemetryInterval().toSeconds(),
                    configuration.telemetryInterval().toSeconds(), TimeUnit.SECONDS);
            offlineFactory.emitCommand("offline-command-1", true);
        } catch (Exception exception) {
            close();
            throw new IllegalStateException("Standalone simulator could not start safely.", exception);
        }
    }

    public synchronized SimulatorConnectionState state() {
        return client == null ? SimulatorConnectionState.BOOTSTRAP_PENDING : client.state();
    }

    public synchronized SimulatorLifecycleState lifecycleState() {
        return lifecycle.state();
    }

    private void safePublishTelemetry() {
        try {
            publishTelemetry();
        } catch (RuntimeException | SimulatorMqttException exception) {
            System.err.println("[offline] telemetry failure: " + exception.getMessage());
        }
    }

    private synchronized void publishTelemetry() throws SimulatorMqttException {
        temperature = bounded(temperature + ((Math.random() - 0.5) * 0.5), 24, 32);
        humidity = bounded(humidity + ((Math.random() - 0.5) * 1.2), 45, 75);
        rssi = (int) bounded(rssi + ((Math.random() - 0.5) * 4), -75, -40);
        SimulatorTelemetry telemetry = new SimulatorTelemetry(
                configuration.identity().deviceKey(), Instant.now(), Map.of(
                        "temperature", temperature, "humidity", humidity,
                        "battery", battery, "rssi", rssi));
        client.publishTelemetry(telemetry, codec, 1);
        System.out.println("[offline] telemetry generated for " + configuration.identity().deviceKey());
    }

    private synchronized void handleCommand(String topic, byte[] payload) {
        try {
            SimulatorCommandResult result = commandProcessor.process(topic, payload);
            client.publishCommandResult(codec.commandResult(result), 1);
            if (result.status() == SimulatorCommandResult.Status.ACCEPTED) {
                client.publishState(commandProcessor.currentState(), codec, 1);
            }
            System.out.println("[offline] command result=" + result.status());
        } catch (Exception exception) {
            System.err.println("[offline] command handling failure: " + exception.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        scheduler.shutdownNow();
        if (client != null) {
            try {
                client.shutdown();
            } catch (SimulatorMqttException exception) {
                System.err.println("Simulator shutdown warning: " + exception.getMessage());
            }
        }
        configuration.close();
        if (started) {
            System.out.println("Java simulator stopped safely.");
        }
        started = false;
    }

    private double bounded(double value, double minimum, double maximum) {
        return Math.round(Math.max(minimum, Math.min(maximum, value)) * 10.0) / 10.0;
    }
}
