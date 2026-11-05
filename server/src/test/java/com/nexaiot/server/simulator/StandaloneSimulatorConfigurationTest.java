package com.nexaiot.server.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StandaloneSimulatorConfigurationTest {

    @Test void defaultsToOfflineModeAndValidBuiltInDevice() {
        try (StandaloneSimulatorConfiguration configuration =
                StandaloneSimulatorConfiguration.fromArgs(new String[0])) {
            assertEquals(StandaloneSimulatorConfiguration.Mode.OFFLINE, configuration.mode());
            assertEquals("NEXA-JAVA-SIM-01", configuration.identity().deviceKey());
            assertTrue(configuration.telemetryInterval().toSeconds() > 0);
        }
    }

    @Test void acceptsDeviceBrokerManifestAndIntervalOverrides() {
        try (StandaloneSimulatorConfiguration configuration =
                StandaloneSimulatorConfiguration.fromArgs(new String[] {
                        "--device-key=NEXA-CUSTOM-1", "--hardware-identity=fixture-hardware",
                        "--broker-uri=ssl://127.0.0.1:1885", "--interval-seconds=2" })) {
            assertEquals("NEXA-CUSTOM-1", configuration.identity().deviceKey());
            assertEquals(2, configuration.telemetryInterval().toSeconds());
            assertEquals("ssl", configuration.brokerUri().getScheme());
        }
    }

    @Test void rejectsMalformedArgumentsAndIntervals() {
        assertThrows(IllegalArgumentException.class,
                () -> StandaloneSimulatorConfiguration.fromArgs(new String[] { "offline" }));
        assertThrows(IllegalArgumentException.class,
                () -> StandaloneSimulatorConfiguration.fromArgs(new String[] { "--interval-seconds=0" }));
        assertThrows(IllegalArgumentException.class,
                () -> StandaloneSimulatorConfiguration.fromArgs(new String[] { "--password=not-safe" }));
    }

    @Test void liveModeRequiresTlsCaAndCredentialsWithoutConnecting() {
        assertThrows(IllegalArgumentException.class,
                () -> StandaloneSimulatorConfiguration.fromArgs(new String[] {
                        "--mode=live", "--broker-uri=tcp://127.0.0.1:1885" }));
    }
}
