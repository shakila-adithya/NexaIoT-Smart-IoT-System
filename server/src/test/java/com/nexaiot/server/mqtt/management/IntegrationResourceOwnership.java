package com.nexaiot.server.mqtt.management;

import java.util.List;

final class IntegrationResourceOwnership {

    private IntegrationResourceOwnership() {
    }

    static void requireClientOwnership(IntegrationRunReceipt receipt,
                                       BrokerDeviceIdentityStatus actual) {
        if (!receipt.clientCreationConfirmed()
                || !receipt.username().equals(actual.username())
                || !receipt.clientId().equals(actual.clientId())) {
            throw manualReconciliation("Disposable client ownership could not be proven.");
        }
    }

    static void requireRoleOwnership(IntegrationRunReceipt receipt,
                                     BrokerRoleStatus actual) {
        List<String> references = actual.referencedClients();
        if (!receipt.roleCreationConfirmed()
                || !receipt.roleName().equals(actual.roleName())
                || !actual.referencesKnown()
                || references.stream().anyMatch(username -> !receipt.username().equals(username))
                || !actual.referencedGroups().isEmpty()) {
            throw manualReconciliation("Disposable role ownership could not be proven.");
        }
    }

    static IllegalStateException manualReconciliation(String message) {
        return new IllegalStateException("MANUAL_RECONCILIATION_REQUIRED: " + message);
    }
}
