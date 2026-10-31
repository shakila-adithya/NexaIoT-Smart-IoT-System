package com.nexaiot.server.mqtt;

import java.util.Optional;

/** Persistence adapter for command state; implementations must atomically reject duplicates. */
public interface MqttCommandStatusStore {

    default void registerPending(MqttPendingCommand pending) {
    }

    default void cancelPending(MqttPendingCommand pending) {
    }

    Optional<MqttPendingCommand> findPending(String deviceKey, String commandId);

    default Optional<MqttCommandResultStatus> findResult(String deviceKey, String commandId) {
        return Optional.empty();
    }

    boolean recordResult(MqttCommandResultStatus result);
}
