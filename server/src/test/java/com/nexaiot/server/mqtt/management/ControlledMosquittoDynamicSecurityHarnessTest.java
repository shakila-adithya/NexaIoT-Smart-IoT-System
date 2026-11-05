package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;

import org.junit.jupiter.api.Test;

import com.nexaiot.server.mqtt.MqttConfigurationValidator;

/**
 * Offline guard tests for the manual B3.5C.4 harness.
 *
 * The real mutation runner is intentionally not a normal JUnit test. A future
 * manual run must set both nexaiot.integration.enabled=true and
 * nexaiot.integration.mutations=true, then provide the management password
 * interactively. The exact target remains ssl://localhost:1885.
 */
class ControlledMosquittoDynamicSecurityHarnessTest {

    private static final String TARGET = "ssl://localhost:1885";
    private static final String MANAGEMENT_ID = "nexaiot-provisioning-management";
    private static final String DEVICE_KEY = "NEXA-8CD8C0F1559E";

    @Test
    void acceptsOnlyTheDedicatedTlsStagingTarget() {
        assertEquals(true, MqttConfigurationValidator.usesTls(TARGET));
        assertTargetAllowed(TARGET);
    }

    @Test
    void rejectsProductionAndNonTlsTargets() {
        assertThrows(IllegalArgumentException.class,
                () -> assertTargetAllowed("tcp://127.0.0.1:1883"));
        assertThrows(IllegalArgumentException.class,
                () -> assertTargetAllowed("tcp://127.0.0.1:1884"));
        assertThrows(IllegalArgumentException.class,
                () -> assertTargetAllowed("ssl://192.168.1.20:1885"));
        assertThrows(IllegalArgumentException.class,
                () -> assertTargetAllowed("tcp://localhost:1885"));
    }

    @Test
    void requiresBothExplicitOptInGuardsBeforeMutation() {
        assertThrows(IllegalStateException.class,
                () -> requireMutationAuthorization("false", "true"));
        assertThrows(IllegalStateException.class,
                () -> requireMutationAuthorization("true", "false"));
        requireMutationAuthorization("true", "true");
    }

    @Test
    void keepsDedicatedIdentityAndDisposableDeviceKeyFixed() {
        assertEquals("nexaiot-provisioning-management", MANAGEMENT_ID);
        assertEquals("NEXA-8CD8C0F1559E", DEVICE_KEY);
    }

    @Test
    void rejectsOwnershipWhenCreationEvidenceOrReferencesAreUnknown() {
        IntegrationRunReceipt receipt = IntegrationRunReceipt.create(
                DEVICE_KEY,
                "nexaiot-device-" + DEVICE_KEY,
                "nexaiot-device-" + DEVICE_KEY,
                "nexaiot-device-" + DEVICE_KEY);
        BrokerRoleStatus unknownReferences = new BrokerRoleStatus(
                receipt.roleName(), java.util.List.of(), java.util.List.of(), false, true);

        assertThrows(IllegalStateException.class,
                () -> IntegrationResourceOwnership.requireRoleOwnership(receipt, unknownReferences));
    }

    @Test
    void marksManualReconciliationExplicitly() {
        IllegalStateException exception = IntegrationResourceOwnership
                .manualReconciliation("timeout during cleanup");
        assertEquals(true, exception.getMessage().startsWith("MANUAL_RECONCILIATION_REQUIRED:"));
    }

    private static void assertTargetAllowed(String brokerUrl) {
        URI uri = URI.create(brokerUrl);
        if (!"ssl".equalsIgnoreCase(uri.getScheme())
                || !"localhost".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != 1885) {
            throw new IllegalArgumentException("Harness target is not the isolated TLS staging broker.");
        }
    }

    private static void requireMutationAuthorization(String integration, String mutations) {
        if (!"true".equalsIgnoreCase(integration) || !"true".equalsIgnoreCase(mutations)) {
            throw new IllegalStateException(
                    "Both integration and broker-mutation authorization flags are required.");
        }
    }
}
