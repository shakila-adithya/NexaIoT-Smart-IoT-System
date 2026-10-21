package com.nexaiot.server.mqtt;

import java.util.Optional;

/** Persistence adapter for command state; implementations must atomically reject duplicates. */
public interface MqttCommandStatusStore {

    Optional<MqttPendingCommand> findPending(String deviceKey, String commandId);

    boolean recordResult(MqttCommandResultStatus result);
}
