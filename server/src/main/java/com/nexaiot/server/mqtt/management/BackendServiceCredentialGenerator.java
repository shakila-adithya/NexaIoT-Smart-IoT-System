package com.nexaiot.server.mqtt.management;

import java.security.SecureRandom;
import java.util.Base64;

public final class BackendServiceCredentialGenerator {

    private static final int PASSWORD_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public GeneratedDeviceCredential generate(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("Backend client ID is required.");
        }
        byte[] password = new byte[PASSWORD_BYTES];
        secureRandom.nextBytes(password);
        return new GeneratedDeviceCredential(
                BackendMqttServicePolicy.USERNAME,
                clientId,
                Base64.getUrlEncoder().withoutPadding().encodeToString(password));
    }
}
