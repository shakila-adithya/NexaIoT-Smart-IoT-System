package com.nexaiot.server.mqtt.management;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.nexaiot.server.mqtt.MqttTopicSecurity;

@Service
public class DeviceCredentialGenerator {

    private static final int PASSWORD_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public GeneratedDeviceCredential generate(String deviceKey) {
        MqttTopicSecurity.validateDeviceKey(deviceKey);
        String identity = "nexaiot-device-" + deviceKey;
        byte[] password = new byte[PASSWORD_BYTES];
        secureRandom.nextBytes(password);
        return new GeneratedDeviceCredential(
                identity,
                identity,
                Base64.getUrlEncoder().withoutPadding().encodeToString(password));
    }
}
