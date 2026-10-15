package com.nexaiot.server.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitServiceTest {

    @Test
    void requestsUnderLimitAreAllowedAndExcessRequestsAreRejected() {
        MutableClock clock = new MutableClock();
        RateLimitService service = new RateLimitService(clock);

        assertDoesNotThrow(() -> service.requireAllowed("login", "10.0.0.1", 2, 60));
        assertDoesNotThrow(() -> service.requireAllowed("login", "10.0.0.1", 2, 60));

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> service.requireAllowed("login", "10.0.0.1", 2, 60));

        assertEquals("Too many requests. Please try again later.", exception.getMessage());
        assertEquals(60, exception.getRetryAfterSeconds());
    }

    @Test
    void identitiesUseSeparateBuckets() {
        RateLimitService service = new RateLimitService(new MutableClock());

        service.requireAllowed("login", "10.0.0.1", 1, 60);

        assertDoesNotThrow(() -> service.requireAllowed("login", "10.0.0.2", 1, 60));
    }

    @Test
    void expiredWindowsAllowRequestsAndAreEvicted() {
        MutableClock clock = new MutableClock();
        RateLimitService service = new RateLimitService(clock);

        service.requireAllowed("login", "10.0.0.1", 1, 10);
        assertEquals(1, service.trackedWindowCount());

        clock.advanceSeconds(11);

        assertDoesNotThrow(() -> service.requireAllowed("login", "10.0.0.1", 1, 10));
        assertEquals(1, service.trackedWindowCount());
    }

    private static final class MutableClock extends Clock {
        private Instant current = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneOffset getZone() { return ZoneOffset.UTC; }

        @Override
        public Clock withZone(java.time.ZoneId zone) { return this; }

        @Override
        public Instant instant() { return current; }

        private void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }
    }
}
