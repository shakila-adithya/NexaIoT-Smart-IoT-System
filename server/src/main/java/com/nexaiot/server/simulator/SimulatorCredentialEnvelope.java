package com.nexaiot.server.simulator;

import java.util.Arrays;

/**
 * Short-lived in-memory credential handoff for the local simulator only.
 * Callers must close it promptly; the password is wiped on close.
 */
public final class SimulatorCredentialEnvelope implements AutoCloseable {

    private final String username;
    private final String clientId;
    private char[] password;

    SimulatorCredentialEnvelope(String username, String clientId, String password) {
        this.username = username;
        this.clientId = clientId;
        this.password = password.toCharArray();
    }

    public String username() { return username; }
    public String clientId() { return clientId; }

    public synchronized char[] passwordCopy() {
        if (password == null) {
            throw new IllegalStateException("Credential envelope is closed.");
        }
        return Arrays.copyOf(password, password.length);
    }

    @Override
    public synchronized void close() {
        if (password != null) {
            Arrays.fill(password, '\0');
            password = null;
        }
    }

    @Override
    public String toString() {
        return "SimulatorCredentialEnvelope[username=" + username
                + ", clientId=" + clientId + ", password=<redacted>]";
    }
}
