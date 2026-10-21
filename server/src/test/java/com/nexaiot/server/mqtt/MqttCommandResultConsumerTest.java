package com.nexaiot.server.mqtt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class MqttCommandResultConsumerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String DEVICE = "NEXA-RESULT-1";
    private static final String TOPIC = "nexaiot/devices/" + DEVICE + "/command-results";

    @Test void validAcceptedResultUpdatesStatus() {
        Store store = store();
        assertTrue(consumer(store).consume(TOPIC, result("ACCEPTED", NOW)));
        assertTrue(store.recorded);
    }

    @Test void duplicateResultIsRejectedByAtomicStore() {
        Store store = store();
        store.recorded = true;
        assertFalse(consumer(store).consume(TOPIC, result("ACCEPTED", NOW)));
    }

    @Test void wrongDeviceTopicIsRejected() {
        assertFalse(consumer(store()).consume("nexaiot/devices/NEXA-OTHER/command-results",
                result("ACCEPTED", NOW)));
    }

    @Test void unknownCommandIsRejected() {
        Store store = store();
        store.pending = Optional.empty();
        assertFalse(consumer(store).consume(TOPIC, result("ACCEPTED", NOW)));
    }

    @Test void wrongCorrelationIsRejected() {
        Store store = store();
        assertFalse(consumer(store).consume(TOPIC, resultWithCorrelation("other", NOW)));
    }

    @Test void rejectedCommandResultIsStillAStatusUpdate() {
        Store store = store();
        assertTrue(consumer(store).consume(TOPIC, result("REJECTED", NOW)));
        assertTrue(store.recorded);
    }

    @Test void staleResultIsRejected() {
        assertFalse(consumer(store()).consume(TOPIC, result("ACCEPTED", NOW.minusSeconds(31))));
    }

    @Test void futureResultIsRejected() {
        assertFalse(consumer(store()).consume(TOPIC, result("ACCEPTED", NOW.plusSeconds(1))));
    }

    @Test void malformedOrOversizedPayloadIsRejected() {
        assertFalse(consumer(store()).consume(TOPIC, "not-json".getBytes()));
        assertFalse(consumer(store()).consume(TOPIC, new byte[8193]));
    }

    @Test void resultConsumerDoesNotExecuteCommandsOrTouchDeviceState() {
        Store store = store();
        assertTrue(consumer(store).consume(TOPIC, result("ACCEPTED", NOW)));
        assertFalse(store.deviceStateChanged);
    }

    private MqttCommandResultConsumer consumer(Store store) {
        return new MqttCommandResultConsumer(new ObjectMapper(), store, Duration.ofSeconds(30),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Store store() {
        Store store = new Store();
        store.pending = Optional.of(new MqttPendingCommand(DEVICE, "cmd-1", "corr-1"));
        return store;
    }

    private byte[] result(String status, Instant timestamp) {
        return resultWithCorrelation("corr-1", status, timestamp);
    }

    private byte[] resultWithCorrelation(String correlation, Instant timestamp) {
        return resultWithCorrelation(correlation, "ACCEPTED", timestamp);
    }

    private byte[] resultWithCorrelation(String correlation, String status, Instant timestamp) {
        return ("{\"commandId\":\"cmd-1\",\"correlationId\":\"" + correlation
                + "\",\"status\":\"" + status + "\",\"processedAt\":\""
                + timestamp + "\"}").getBytes();
    }

    private static final class Store implements MqttCommandStatusStore {
        private Optional<MqttPendingCommand> pending = Optional.empty();
        private boolean recorded;
        private boolean deviceStateChanged;
        private final Map<String, MqttCommandResultStatus> results = new HashMap<>();

        @Override public Optional<MqttPendingCommand> findPending(String deviceKey, String commandId) {
            return pending.filter(value -> value.deviceKey().equals(deviceKey)
                    && value.commandId().equals(commandId));
        }

        @Override public boolean recordResult(MqttCommandResultStatus result) {
            if (recorded || results.putIfAbsent(result.deviceKey() + ":" + result.commandId(), result) != null) {
                return false;
            }
            recorded = true;
            return true;
        }
    }
}
