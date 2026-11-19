package com.nexaiot.server.mqtt;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

class InMemoryMqttCommandStatusStoreTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private final MutableClock clock = new MutableClock();
    private final InMemoryMqttCommandStatusStore store = new InMemoryMqttCommandStatusStore(clock, 2);

    @Test
    void deadlineExpiresAtExactBoundaryAndRetainsTimeout() {
        store.registerPending(command("one"));
        clock.now = START.plusSeconds(29);
        assertEquals("PENDING", status("one"));
        clock.now = START.plusSeconds(30);
        assertEquals("TIMEOUT", status("one"));
        assertTrue(store.findPending("device", "one").isEmpty());
        clock.now = START.plusSeconds(60);
        assertEquals("TIMEOUT", status("one"));
        assertTrue(store.findResult("device", "one").orElseThrow().reason().contains("may still have acted"));
    }

    @Test
    void timelyAcknowledgementStaysTerminalAfterDeadline() {
        store.registerPending(command("one"));
        clock.now = START.plusSeconds(20);
        assertTrue(store.recordResult(result("one", "ACCEPTED")));
        assertTrue(store.findPending("device", "one").isEmpty());
        clock.now = START.plusSeconds(60);
        assertEquals("ACCEPTED", status("one"));
        assertFalse(store.recordResult(result("one", "REJECTED")));
        assertEquals("ACCEPTED", status("one"));
    }

    @Test
    void lateReplyCannotReviveExpiredCommandEvenWithEarlierDeviceTimestamp() {
        store.registerPending(command("one"));
        clock.now = START.plusSeconds(30);
        assertFalse(store.recordResult(new MqttCommandResultStatus("device", "one", "corr", "ACCEPTED", null, START.plusSeconds(2))));
        assertEquals("TIMEOUT", status("one"));
    }

    @Test
    void unknownAndCrossDeviceCommandsAreNotTimeouts() {
        store.registerPending(command("one"));
        assertTrue(store.findStatus("device", "missing").isEmpty());
        assertTrue(store.findStatus("other", "one").isEmpty());
        assertFalse(store.recordResult(new MqttCommandResultStatus("other", "one", "corr", "ACCEPTED", null, START)));
    }

    @Test
    void fullStoreRejectsNewCommandsRatherThanEvictingPendingWork() {
        store.registerPending(command("one"));
        store.registerPending(command("two"));
        assertThrows(CommandTrackingCapacityException.class, () -> store.registerPending(command("three")));
        assertEquals("PENDING", status("one"));
        assertTrue(store.findStatus("device", "three").isEmpty());
    }

    @Test
    void terminalEntriesCanBeEvictedWithoutLosingOtherPendingCommands() {
        store.registerPending(command("one"));
        store.registerPending(command("two"));
        store.recordResult(result("one", "REJECTED"));
        store.registerPending(command("three"));
        assertTrue(store.findStatus("device", "one").isEmpty());
        assertEquals("PENDING", status("two"));
        assertEquals("PENDING", status("three"));
    }

    @Test
    void duplicateIdsAndWrongCorrelationsAreRejected() {
        store.registerPending(command("one"));
        assertThrows(IllegalArgumentException.class, () -> store.registerPending(command("one")));
        assertFalse(store.recordResult(new MqttCommandResultStatus("device", "one", "wrong", "ACCEPTED", null, START)));
        assertEquals("PENDING", status("one"));
    }

    @Test
    void cancelledPublishIsUnknownButCancellationCannotEraseAnAcknowledgement() {
        MqttPendingCommand one = command("one");
        store.registerPending(one);
        store.cancelPending(one);
        assertTrue(store.findStatus("device", "one").isEmpty());
        store.registerPending(one);
        store.recordResult(result("one", "ACCEPTED"));
        store.cancelPending(one);
        assertEquals("ACCEPTED", status("one"));
    }

    @Test
    void realConsumerRejectsFreshReplyReceivedAfterServerDeadline() {
        store.registerPending(command("one"));
        clock.now = START.plusSeconds(31);
        var consumer = new MqttCommandResultConsumer(new ObjectMapper(), store, Duration.ofSeconds(30), clock);
        String payload = "{\"commandId\":\"one\",\"correlationId\":\"corr\",\"capabilityKey\":\"power\","
                + "\"status\":\"ACCEPTED\",\"processedAt\":\"" + clock.now + "\"}";
        assertFalse(consumer.consume("nexaiot/devices/device/command-results", payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("TIMEOUT", status("one"));
    }

    private String status(String commandId) {
        return store.findStatus("device", commandId).orElseThrow().status();
    }

    private MqttPendingCommand command(String id) {
        return new MqttPendingCommand("device", id, "corr", "power", START, START.plusSeconds(30));
    }

    private MqttCommandResultStatus result(String id, String status) {
        return new MqttCommandResultStatus("device", id, "corr", status, null, clock.instant());
    }

    static class MutableClock extends Clock {
        Instant now = START;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
