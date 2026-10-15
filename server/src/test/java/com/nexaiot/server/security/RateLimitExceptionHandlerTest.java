package com.nexaiot.server.security;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.nexaiot.server.exception.GlobalExceptionHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitExceptionHandlerTest {

    @Test
    void throttledResponseUsesSafeMessageAndRetryAfter() {
        ResponseEntity<Map<String, String>> response = new GlobalExceptionHandler()
                .handleRateLimit(new RateLimitExceededException(12));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("12", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals(
                "Too many requests. Please try again later.",
                response.getBody().get("message")
        );
    }
}
