package com.nexaiot.server.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StandaloneSimulatorRunnerTest {

    @Test void startsOfflineDeviceGeneratesTrafficAndHandlesDemoCommand() {
        try (StandaloneSimulatorConfiguration configuration =
                StandaloneSimulatorConfiguration.fromArgs(new String[] { "--interval-seconds=1" });
                StandaloneSimulatorRunner runner = new StandaloneSimulatorRunner(configuration)) {
            runner.start();
            assertEquals(SimulatorConnectionState.ACTIVE, runner.state());
            assertEquals(SimulatorLifecycleState.ACTIVE, runner.lifecycleState());
        }
    }

    @Test void liveModeIsExplicitlyBlockedUntilSecureOnboardingExists() {
        assertThrows(IllegalArgumentException.class,
                () -> StandaloneSimulatorConfiguration.fromArgs(new String[] {
                        "--mode=live", "--broker-uri=ssl://127.0.0.1:1885" }));
    }

    @Test void shutdownIsSafeBeforeStartupAndAfterStartup() {
        try (StandaloneSimulatorConfiguration configuration =
                StandaloneSimulatorConfiguration.fromArgs(new String[0])) {
            StandaloneSimulatorRunner runner = new StandaloneSimulatorRunner(configuration);
            runner.close();
            runner.close();
            assertEquals(SimulatorConnectionState.BOOTSTRAP_PENDING, runner.state());
        }
    }
}
