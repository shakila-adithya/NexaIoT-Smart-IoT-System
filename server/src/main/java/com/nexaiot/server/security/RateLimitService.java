package com.nexaiot.server.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Small per-instance fixed-window limiter for high-risk operations.
 *
 * Keys are hashed so normalized emails, reset tokens, and device identifiers
 * are not retained in plaintext in the in-memory map.
 */
@Service
public class RateLimitService {

    private static final int MAX_TRACKED_WINDOWS = 10_000;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimitService() {
        this(Clock.systemUTC());
    }

    RateLimitService(Clock clock) {
        this.clock = clock;
    }

    public void requireAllowed(
            String bucket,
            String identity,
            int maximumRequests,
            long windowSeconds
    ) {
        if (maximumRequests <= 0 || windowSeconds <= 0) {
            throw new IllegalArgumentException("Rate-limit configuration must be positive.");
        }

        Instant now = clock.instant();
        long windowMillis = Duration.ofSeconds(windowSeconds).toMillis();
        String key = hash(bucket + ":" + (identity == null ? "" : identity));

        windows.entrySet().removeIf(entry ->
                now.toEpochMilli() - entry.getValue().startedAtMillis >= entry.getValue().windowMillis);

        if (!windows.containsKey(key) && windows.size() >= MAX_TRACKED_WINDOWS) {
            var iterator = windows.keySet().iterator();
            if (iterator.hasNext()) {
                windows.remove(iterator.next());
            }
        }

        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null
                    || now.toEpochMilli() - current.startedAtMillis >= current.windowMillis) {
                return new Window(now.toEpochMilli(), windowMillis, 1);
            }
            if (current.count >= maximumRequests) {
                return new Window(current.startedAtMillis, current.windowMillis, maximumRequests + 1);
            }
            return new Window(current.startedAtMillis, current.windowMillis, current.count + 1);
        });

        if (window.count > maximumRequests) {
            long elapsed = Math.max(0, now.toEpochMilli() - window.startedAtMillis);
            long retryAfter = Math.max(1, (window.windowMillis - elapsed + 999) / 1000);
            throw new RateLimitExceededException(retryAfter);
        }
    }

    int trackedWindowCount() {
        return windows.size();
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to create rate-limit key.", exception);
        }
    }

    private record Window(long startedAtMillis, long windowMillis, int count) {
    }
}
