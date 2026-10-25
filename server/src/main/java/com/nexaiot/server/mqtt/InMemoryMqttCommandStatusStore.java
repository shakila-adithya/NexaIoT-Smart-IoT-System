package com.nexaiot.server.mqtt;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class InMemoryMqttCommandStatusStore implements MqttCommandStatusStore {

    private static final int MAX_ENTRIES = 4096;

    private final Map<String, MqttPendingCommand> pending = new LinkedHashMap<>();
    private final Map<String, MqttCommandResultStatus> results = new LinkedHashMap<>();

    @Override
    public synchronized void registerPending(MqttPendingCommand command) {
        String key = key(command.deviceKey(), command.commandId());
        if (pending.containsKey(key) || results.containsKey(key)) {
            throw new IllegalArgumentException("Command identifier is already registered.");
        }
        pending.put(key, command);
        trim();
    }

    @Override
    public synchronized void cancelPending(MqttPendingCommand command) {
        pending.remove(key(command.deviceKey(), command.commandId()));
    }

    @Override
    public synchronized Optional<MqttPendingCommand> findPending(
            String deviceKey,
            String commandId
    ) {
        return Optional.ofNullable(pending.get(key(deviceKey, commandId)));
    }

    @Override
    public synchronized boolean recordResult(MqttCommandResultStatus result) {
        String key = key(result.deviceKey(), result.commandId());
        if (!pending.containsKey(key) || results.containsKey(key)) {
            return false;
        }
        results.put(key, result);
        trim();
        return true;
    }

    synchronized Optional<MqttCommandResultStatus> findResult(
            String deviceKey,
            String commandId
    ) {
        return Optional.ofNullable(results.get(key(deviceKey, commandId)));
    }

    private String key(String deviceKey, String commandId) {
        return deviceKey + ":" + commandId;
    }

    private void trim() {
        while (pending.size() + results.size() > MAX_ENTRIES) {
            if (!pending.isEmpty()) {
                pending.remove(pending.keySet().iterator().next());
            } else {
                results.remove(results.keySet().iterator().next());
            }
        }
    }
}
