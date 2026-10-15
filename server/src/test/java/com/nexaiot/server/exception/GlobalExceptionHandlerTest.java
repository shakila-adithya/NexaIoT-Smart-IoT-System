package com.nexaiot.server.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedExceptionUsesGenericServerResponse() {
        var response = handler.handleUnexpected(new RuntimeException("internal implementation detail"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred.", response.getBody().get("message"));
    }

    @Test
    void internalIllegalArgumentDetailsAreNotReturned() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("MongoDB connection URI failed"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred.", response.getBody().get("message"));
    }

    @Test
    void expectedDomainValidationMessageRemainsUsable() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("Only JPEG and PNG images are supported"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Only JPEG and PNG images are supported", response.getBody().get("message"));
    }
}
