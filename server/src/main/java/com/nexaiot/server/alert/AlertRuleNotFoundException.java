package com.nexaiot.server.alert;

public class AlertRuleNotFoundException extends RuntimeException {
    public AlertRuleNotFoundException(String message) {
        super(message);
    }
}
