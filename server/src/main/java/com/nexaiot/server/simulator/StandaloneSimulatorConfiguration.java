package com.nexaiot.server.simulator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityDataType;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.device.CapabilityManifestSource;

/** Validated CLI/environment configuration for the standalone simulator. */
public final class StandaloneSimulatorConfiguration implements AutoCloseable {

    public enum Mode { OFFLINE, LIVE }

    private final Mode mode;
    private final SimulatorDeviceIdentity identity;
    private final CapabilityManifest manifest;
    private final URI brokerUri;
    private final Path caCertificate;
    private final String username;
    private char[] password;
    private final Duration telemetryInterval;

    private StandaloneSimulatorConfiguration(Mode mode, SimulatorDeviceIdentity identity,
            CapabilityManifest manifest, URI brokerUri, Path caCertificate, String username,
            char[] password, Duration telemetryInterval) {
        this.mode = mode;
        this.identity = identity;
        this.manifest = manifest;
        this.brokerUri = brokerUri;
        this.caCertificate = caCertificate;
        this.username = username;
        this.password = password == null ? new char[0] : password.clone();
        this.telemetryInterval = telemetryInterval;
    }

    public static StandaloneSimulatorConfiguration fromArgs(String[] args) {
        Map<String, String> values = new HashMap<>();
        for (String arg : args == null ? new String[0] : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("Arguments must use --name=value syntax.");
            }
            int separator = arg.indexOf('=');
            values.put(arg.substring(2, separator), arg.substring(separator + 1));
        }
        if (values.containsKey("password")) {
            throw new IllegalArgumentException(
                    "Password must be supplied through NEXAIOT_SIMULATOR_PASSWORD, not the command line.");
        }

        Mode mode = Mode.valueOf(value(values, "mode", "offline").trim().toUpperCase());
        String deviceKey = value(values, "device-key", "NEXA-JAVA-SIM-01");
        String hardware = value(values, "hardware-identity", "offline-simulator-hardware");
        SimulatorDeviceIdentity identity = SimulatorDeviceIdentity.forDeviceKey(deviceKey, hardware);
        CapabilityManifest manifest = loadManifest(values.get("manifest"));
        URI broker = URI.create(value(values, "broker-uri", "ssl://127.0.0.1:1885"));
        Path ca = Path.of(value(values, "ca-certificate", "offline-mock-ca.crt"));
        long intervalSeconds = Long.parseLong(value(values, "interval-seconds", "5"));
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("Telemetry interval must be positive.");
        }

        String username = values.getOrDefault("username", env("NEXAIOT_SIMULATOR_USERNAME", ""));
        String configuredPassword = env("NEXAIOT_SIMULATOR_PASSWORD", "");
        if (mode == Mode.LIVE) {
            if (!"ssl".equalsIgnoreCase(broker.getScheme()) || broker.getHost() == null
                    || broker.getPort() <= 0) {
                throw new IllegalArgumentException("Live simulator mode requires ssl://host:port.");
            }
            if (ca.toString().isBlank() || !Files.isRegularFile(ca)) {
                throw new IllegalArgumentException("Live simulator mode requires an existing CA certificate.");
            }
            if (username.isBlank() || configuredPassword.isBlank()) {
                throw new IllegalArgumentException("Live simulator credentials must be supplied securely.");
            }
        }
        return new StandaloneSimulatorConfiguration(mode, identity, manifest, broker, ca,
                username, configuredPassword.toCharArray(), Duration.ofSeconds(intervalSeconds));
    }

    public Mode mode() { return mode; }
    public SimulatorDeviceIdentity identity() { return identity; }
    public CapabilityManifest manifest() { return manifest; }
    public URI brokerUri() { return brokerUri; }
    public Path caCertificate() { return caCertificate; }
    public String username() { return username; }
    public char[] passwordCopy() { return password.clone(); }
    public Duration telemetryInterval() { return telemetryInterval; }

    @Override
    public void close() {
        java.util.Arrays.fill(password, '\0');
        password = new char[0];
    }

    private static String value(Map<String, String> values, String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }

    private static CapabilityManifest loadManifest(String path) {
        if (path == null || path.isBlank()) {
            return defaultManifest();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(Files.readString(Path.of(path)));
            JsonNode manifestNode = root.has("capabilityManifest")
                    ? root.get("capabilityManifest") : root;
            CapabilityManifest manifest = mapper.treeToValue(manifestNode, CapabilityManifest.class);
            if (!com.nexaiot.server.device.CapabilityManifestValidator.isValid(manifest)) {
                throw new IllegalArgumentException("Capability manifest is invalid.");
            }
            return manifest;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Capability manifest could not be loaded.", exception);
        }
    }

    private static CapabilityManifest defaultManifest() {
        CapabilityManifest manifest = new CapabilityManifest(CapabilityManifestSource.CUSTOM);
        manifest.setTemplateId("standalone-java-simulator");
        manifest.setCapabilities(java.util.List.of(
                measurement("temperature"), measurement("humidity"), measurement("battery"),
                measurement("rssi"), control("power")));
        return manifest;
    }

    private static CapabilityDefinition measurement(String key) {
        CapabilityDefinition definition = new CapabilityDefinition();
        definition.setKey(key);
        definition.setName(key);
        definition.setCategory(CapabilityCategory.MEASUREMENT);
        definition.setDataType(CapabilityDataType.NUMBER);
        return definition;
    }

    private static CapabilityDefinition control(String key) {
        CapabilityDefinition definition = new CapabilityDefinition();
        definition.setKey(key);
        definition.setName(key);
        definition.setCategory(CapabilityCategory.CONTROL);
        definition.setDataType(CapabilityDataType.BOOLEAN);
        definition.setControl(new com.nexaiot.server.device.CapabilityControl(
                com.nexaiot.server.device.CapabilityControlType.SWITCH));
        return definition;
    }
}
