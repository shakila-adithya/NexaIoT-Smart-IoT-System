package com.nexaiot.server.enrollment;

public class EnrollmentConcurrencyException extends RuntimeException {

    public EnrollmentConcurrencyException(String message) {
        super(message);
    }
}
