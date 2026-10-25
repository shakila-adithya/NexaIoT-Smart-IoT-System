package com.nexaiot.server.mqtt;

import java.util.Optional;

/** Persistence adapter for command state; implementations must atomically reject duplicates. */
public interface MqttCommandStatusStore {

    default void registerPending(MqttPendingCommand pending) {
    }

    default void cancelPending(MqttPendingCommand pending) {
    }

    Optional<MqttPendingCommand> findPending(String deviceKey, String commandId);

    boolean recordResult(MqttCommandResultStatus result);
}
