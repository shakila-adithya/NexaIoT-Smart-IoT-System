package com.nexaiot.server.mqtt;

public class CommandTrackingCapacityException extends RuntimeException {
    public CommandTrackingCapacityException() {
        super("Too many commands are awaiting acknowledgement. Please retry later.");
    }
}
