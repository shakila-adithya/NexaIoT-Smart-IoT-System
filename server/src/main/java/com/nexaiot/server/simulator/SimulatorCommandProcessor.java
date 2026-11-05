package com.nexaiot.server.simulator;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.nexaiot.server.device.CapabilityManifest;

/** Applies safe, idempotent capability commands to simulator state. */
public final class SimulatorCommandProcessor {

    private static final int MAX_PROCESSED_COMMANDS = 1024;

    private final SimulatorDeviceIdentity identity;
    private final CapabilityManifest manifest;
    private final SimulatorMessageCodec codec;
    private final Clock clock;
    private final Map<String, SimulatorCommandResult> processed =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, SimulatorCommandResult> eldest) {
                    return size() > MAX_PROCESSED_COMMANDS;
                }
            };
    private final Map<String, Object> state = new LinkedHashMap<>();

    public SimulatorCommandProcessor(
            SimulatorDeviceIdentity identity,
            CapabilityManifest manifest,
            SimulatorMessageCodec codec
    ) {
        this(identity, manifest, codec, Clock.systemUTC());
    }

    SimulatorCommandProcessor(
            SimulatorDeviceIdentity identity,
            CapabilityManifest manifest,
            SimulatorMessageCodec codec,
            Clock clock
    ) {
        if (identity == null || manifest == null || codec == null || clock == null) {
            throw new IllegalArgumentException("Simulator command processor is incomplete.");
        }
        this.identity = identity;
        this.manifest = manifest;
        this.codec = codec;
        this.clock = clock;
    }

    public synchronized SimulatorCommandResult process(String topic, byte[] payload) {
        if (!SimulatorMqttTopics.commands(identity).equals(topic)) {
            throw new IllegalArgumentException("Simulator command topic does not match the device.");
        }
        try {
            SimulatorCommand command = codec.decodeCommand(payload);
            String commandId = codec.commandId(command);
            String correlationId = codec.correlationId(command);
            SimulatorCommandResult previous = processed.get(commandId);
            if (previous != null) {
                return new SimulatorCommandResult(commandId, correlationId,
                        command.capabilityKey(), SimulatorCommandResult.Status.DUPLICATE,
                        "Command was already processed.", Instant.now(clock));
            }
            SimulatorCapabilityValidator.validateCommand(manifest, command);
            state.put(command.capabilityKey(), command.value());
            SimulatorCommandResult result = new SimulatorCommandResult(
                    commandId, correlationId, command.capabilityKey(),
                    SimulatorCommandResult.Status.ACCEPTED, null, Instant.now(clock));
            processed.put(commandId, result);
            return result;
        } catch (RuntimeException exception) {
            return new SimulatorCommandResult(null, null, null,
                    SimulatorCommandResult.Status.REJECTED,
                    "Command was rejected.", Instant.now(clock));
        }
    }

    public synchronized SimulatorState currentState() {
        return new SimulatorState(state);
    }
}
