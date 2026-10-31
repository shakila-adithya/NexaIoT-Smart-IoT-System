package com.nexaiot.server.simulator;

import java.util.Map;

/** Current simulator state, derived from its applied capability commands. */
public record SimulatorState(Map<String, Object> values) {

    public SimulatorState {
        if (values == null) {
            throw new IllegalArgumentException("Simulator state is required.");
        }
        values = Map.copyOf(values);
    }
}
