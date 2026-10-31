package com.nexaiot.server.simulator;

import java.time.Duration;

/** Bounded reconnect backoff for a future MQTT adapter. */
public record SimulatorReconnectPolicy(
        Duration initialDelay,
        Duration maximumDelay
) {

    public SimulatorReconnectPolicy {
        if (initialDelay == null || maximumDelay == null
                || initialDelay.isNegative() || initialDelay.isZero()
                || maximumDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("Reconnect delays are invalid.");
        }
    }

    public Duration delayForAttempt(int attempt) {
        if (attempt < 0) {
            throw new IllegalArgumentException("Reconnect attempt cannot be negative.");
        }
        Duration delay = initialDelay;
        for (int index = 0; index < attempt
                && delay.compareTo(maximumDelay) < 0; index++) {
            long nextMillis = Math.min(
                    maximumDelay.toMillis(), Math.max(delay.toMillis() * 2, delay.toMillis() + 1));
            delay = Duration.ofMillis(nextMillis);
        }
        return delay;
    }
}
