package com.nexaiot.server.simulator;

import com.nexaiot.server.device.CapabilityCategory;
import com.nexaiot.server.device.CapabilityDefinition;
import com.nexaiot.server.device.CapabilityManifest;
import com.nexaiot.server.device.CapabilityManifestValidator;

/** Applies the existing universal capability contract to simulator messages. */
public final class SimulatorCapabilityValidator {

    private SimulatorCapabilityValidator() {
    }

    public static void validateTelemetry(
            SimulatorDeviceIdentity identity,
            CapabilityManifest manifest,
            SimulatorTelemetry telemetry
    ) {
        if (identity == null || telemetry == null || !identity.deviceKey().equals(telemetry.deviceKey())
                || !CapabilityManifestValidator.isValid(manifest)) {
            throw new IllegalArgumentException("Simulator telemetry is not valid.");
        }
        telemetry.values().forEach((key, value) -> {
            CapabilityDefinition definition = definition(manifest, key);
            if (definition == null || definition.getCategory() == CapabilityCategory.CONTROL
                    || !matches(definition, value)) {
                throw new IllegalArgumentException("Simulator telemetry capability is not supported.");
            }
        });
    }

    public static void validateCommand(CapabilityManifest manifest, SimulatorCommand command) {
        if (!CapabilityManifestValidator.isValid(manifest) || command == null) {
            throw new IllegalArgumentException("Simulator command is not valid.");
        }
        CapabilityDefinition definition = definition(manifest, command.capabilityKey());
        if (definition == null || definition.getCategory() != CapabilityCategory.CONTROL
                || !matches(definition, command.value())) {
            throw new IllegalArgumentException("Simulator command capability is not supported.");
        }
    }

    private static CapabilityDefinition definition(CapabilityManifest manifest, String key) {
        return manifest.getCapabilities().stream()
                .filter(candidate -> key.equals(candidate.getKey()))
                .findFirst()
                .orElse(null);
    }

    private static boolean matches(CapabilityDefinition definition, Object value) {
        if (value == null) {
            return false;
        }
        return switch (definition.getDataType()) {
            case NUMBER -> value instanceof Number && numberWithinConstraints(definition, (Number) value);
            case BOOLEAN -> value instanceof Boolean;
            case STRING -> value instanceof CharSequence;
            case ENUM -> value instanceof CharSequence
                    && definition.getOptions() != null
                    && definition.getOptions().stream()
                    .map(option -> option.getValue())
                    .toList()
                    .contains(value.toString());
        };
    }

    private static boolean numberWithinConstraints(CapabilityDefinition definition, Number value) {
        double number = value.doubleValue();
        if (!Double.isFinite(number)) {
            return false;
        }
        if (definition.getConstraints() == null) {
            return true;
        }
        var constraints = definition.getConstraints();
        return (constraints.getMin() == null || number >= constraints.getMin())
                && (constraints.getMax() == null || number <= constraints.getMax());
    }
}
