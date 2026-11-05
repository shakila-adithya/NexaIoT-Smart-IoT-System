package com.nexaiot.server.mqtt.management;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeviceCredentialGeneratorTest {

    @Test
    void generatesDeviceBoundIdentityAndHighEntropyPassword() {
        DeviceCredentialGenerator generator = new DeviceCredentialGenerator();

        GeneratedDeviceCredential first = generator.generate("NEXA-TEST-1");
        GeneratedDeviceCredential second = generator.generate("NEXA-TEST-1");

        assertTrue(first.username().contains("NEXA-TEST-1"));
        assertEqualsIdentity(first, second);
        assertNotEquals(first.password(), second.password());
        assertTrue(first.password().length() >= 40);
        assertTrue(first.toString().contains("<redacted>"));
        assertTrue(!first.toString().contains(first.password()));
    }

    private void assertEqualsIdentity(
            GeneratedDeviceCredential first,
            GeneratedDeviceCredential second
    ) {
        assertTrue(first.username().equals(second.username()));
        assertTrue(first.clientId().equals(second.clientId()));
    }
}
