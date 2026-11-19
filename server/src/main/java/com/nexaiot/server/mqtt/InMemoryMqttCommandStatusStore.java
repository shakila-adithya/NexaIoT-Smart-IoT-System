package com.nexaiot.server.mqtt;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Bounded process-local acknowledgement tracking. All transitions use the server clock. */
@Service
public class InMemoryMqttCommandStatusStore implements MqttCommandStatusStore {
    private final Map<String, MqttCommandStatusSnapshot> commands = new LinkedHashMap<>();
    private final Clock clock;
    private final int maximumEntries;

    public InMemoryMqttCommandStatusStore() {
        this(Clock.systemUTC(), 4096);
    }

    InMemoryMqttCommandStatusStore(Clock clock, int maximumEntries) {
        if (clock == null || maximumEntries <= 0) throw new IllegalArgumentException("Invalid command store policy.");
        this.clock = clock;
        this.maximumEntries = maximumEntries;
    }

    @Override
    public synchronized void registerPending(MqttPendingCommand command) {
        Instant now = clock.instant();
        expire(now);
        String key = key(command.deviceKey(), command.commandId());
        if (commands.containsKey(key)) throw new IllegalArgumentException("Command identifier is already registered.");
        if (commands.size() >= maximumEntries) {
            var terminal = commands.entrySet().stream().filter(entry -> entry.getValue().result() != null)
                    .map(Map.Entry::getKey).findFirst();
            if (terminal.isEmpty()) throw new CommandTrackingCapacityException();
            commands.remove(terminal.get());
        }
        commands.put(key, expired(new MqttCommandStatusSnapshot(command, null, now), now));
    }

    @Override
    public synchronized void cancelPending(MqttPendingCommand command) {
        String key = key(command.deviceKey(), command.commandId());
        MqttCommandStatusSnapshot current = commands.get(key);
        if (current != null && current.result() == null) commands.remove(key);
    }

    @Override
    public synchronized Optional<MqttPendingCommand> findPending(String deviceKey, String commandId) {
        return findStatus(deviceKey, commandId).filter(snapshot -> snapshot.result() == null)
                .map(MqttCommandStatusSnapshot::command);
    }

    @Override
    public synchronized Optional<MqttCommandResultStatus> findResult(String deviceKey, String commandId) {
        return findStatus(deviceKey, commandId).map(MqttCommandStatusSnapshot::result);
    }

    @Override
    public synchronized Optional<MqttCommandStatusSnapshot> findStatus(String deviceKey, String commandId) {
        String key = key(deviceKey, commandId);
        MqttCommandStatusSnapshot current = commands.get(key);
        if (current == null) return Optional.empty();
        MqttCommandStatusSnapshot updated = expired(current, clock.instant());
        commands.put(key, updated);
        return Optional.of(updated);
    }

    @Override
    public synchronized boolean recordResult(MqttCommandResultStatus result) {
        var current = findStatus(result.deviceKey(), result.commandId()).orElse(null);
        if (current == null || current.result() != null) return false;
        MqttPendingCommand command = current.command();
        if (!java.util.Objects.equals(command.correlationId(), result.correlationId())
                || !java.util.Set.of("ACCEPTED", "DUPLICATE", "REJECTED").contains(result.status())) return false;
        commands.put(key(result.deviceKey(), result.commandId()),
                new MqttCommandStatusSnapshot(command, result, clock.instant()));
        return true;
    }

    private void expire(Instant now) {
        commands.replaceAll((key, snapshot) -> expired(snapshot, now));
    }

    private MqttCommandStatusSnapshot expired(MqttCommandStatusSnapshot snapshot, Instant now) {
        MqttPendingCommand command = snapshot.command();
        MqttCommandResultStatus result = snapshot.result();
        if (result == null && !now.isBefore(command.expiresAt())) {
            result = new MqttCommandResultStatus(command.deviceKey(), command.commandId(), command.correlationId(),
                    "TIMEOUT", "No device acknowledgement arrived before the deadline. The device may still have acted.",
                    command.expiresAt());
        }
        return new MqttCommandStatusSnapshot(command, result, now);
    }

    private String key(String deviceKey, String commandId) {
        return deviceKey + ":" + commandId;
    }
}
