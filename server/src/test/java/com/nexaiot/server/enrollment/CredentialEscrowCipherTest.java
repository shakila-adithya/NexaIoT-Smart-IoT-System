package com.nexaiot.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.nexaiot.server.mqtt.management.GeneratedDeviceCredential;

class CredentialEscrowCipherTest {

    private static final String OPERATION = "operation-1";
    private static final String DEVICE = "device-1";
    private static final String VERSION = "key-2026-01";
    private static final GeneratedDeviceCredential CREDENTIAL =
            new GeneratedDeviceCredential("nexaiot-device-NEXA-1",
                    "nexaiot-device-NEXA-1", "password-never-logged");

    @Test
    void encryptsAndDecryptsWithAadBoundToEnrollment() {
        CredentialEscrowCipher.EncryptedCredential encrypted = encrypt(key(1), CREDENTIAL);

        GeneratedDeviceCredential recovered = CredentialEscrowCipher.decrypt(
                key(1), encrypted, OPERATION, DEVICE, CREDENTIAL.username(), CREDENTIAL.clientId());

        assertEquals(CREDENTIAL, recovered);
    }

    @Test
    void repeatedEncryptionUsesUniqueNonceAndCiphertext() {
        CredentialEscrowCipher.EncryptedCredential first = encrypt(key(1), CREDENTIAL);
        CredentialEscrowCipher.EncryptedCredential second = encrypt(key(1), CREDENTIAL);

        assertNotEquals(first.nonce(), second.nonce());
        assertNotEquals(first.ciphertext(), second.ciphertext());
    }

    @Test
    void tamperingWrongKeyAndWrongAssociationAreRejected() {
        CredentialEscrowCipher.EncryptedCredential encrypted = encrypt(key(1), CREDENTIAL);
        String tampered = Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 });
        CredentialEscrowCipher.EncryptedCredential changed =
                new CredentialEscrowCipher.EncryptedCredential(
                        encrypted.nonce(), tampered, encrypted.keyVersion());

        assertThrows(IllegalArgumentException.class, () -> CredentialEscrowCipher.decrypt(
                key(1), changed, OPERATION, DEVICE, CREDENTIAL.username(), CREDENTIAL.clientId()));
        assertThrows(IllegalArgumentException.class, () -> CredentialEscrowCipher.decrypt(
                key(2), encrypted, OPERATION, DEVICE, CREDENTIAL.username(), CREDENTIAL.clientId()));
        assertThrows(IllegalArgumentException.class, () -> CredentialEscrowCipher.decrypt(
                key(1), encrypted, "other-operation", DEVICE,
                CREDENTIAL.username(), CREDENTIAL.clientId()));
        assertThrows(IllegalArgumentException.class, () -> CredentialEscrowCipher.decrypt(
                key(1), encrypted, OPERATION, "other-device",
                CREDENTIAL.username(), CREDENTIAL.clientId()));
    }

    private CredentialEscrowCipher.EncryptedCredential encrypt(
            SecretKeySpec key, GeneratedDeviceCredential credential) {
        return CredentialEscrowCipher.encrypt(key, VERSION, OPERATION, DEVICE, credential);
    }

    private SecretKeySpec key(int value) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) value;
        return new SecretKeySpec(bytes, "AES");
    }
}
