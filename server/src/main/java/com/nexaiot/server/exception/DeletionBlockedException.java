package com.nexaiot.server.exception;

import org.springframework.http.HttpStatus;

/** A safe, actionable response when deletion cannot finish. */
public class DeletionBlockedException extends RuntimeException {
    private final HttpStatus status;

    public DeletionBlockedException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
