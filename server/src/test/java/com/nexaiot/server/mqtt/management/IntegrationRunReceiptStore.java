package com.nexaiot.server.mqtt.management;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

/** Writes only non-secret run evidence outside the repository. */
final class IntegrationRunReceiptStore {

    private IntegrationRunReceiptStore() {
    }

    static Path defaultDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) {
            throw new IllegalStateException("LOCALAPPDATA is required for integration receipts.");
        }
        return Path.of(localAppData, "NexaIoT", "mosquitto-tls-staging", "integration-runs");
    }

    static Path write(Path directory, IntegrationRunReceipt receipt) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(receipt.runId() + ".receipt");
        Path temporary = directory.resolve(receipt.runId() + "."
                + UUID.randomUUID() + ".tmp");
        List<String> lines = List.of(
                "runId=" + receipt.runId(),
                "deviceKey=" + receipt.deviceKey(),
                "username=" + receipt.username(),
                "clientId=" + receipt.clientId(),
                "roleName=" + receipt.roleName(),
                "clientCreationConfirmed=" + receipt.clientCreationConfirmed(),
                "roleCreationConfirmed=" + receipt.roleCreationConfirmed(),
                "reconciliationStatus=" + receipt.reconciliationStatus(),
                "createdAt=" + receipt.createdAt());
        try (FileChannel channel = FileChannel.open(temporary,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.write(StandardCharsets.UTF_8.encode(
                    String.join(System.lineSeparator(), lines) + System.lineSeparator()));
            channel.force(true);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic receipt replacement is unsupported.", exception);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }
}
