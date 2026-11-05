package com.nexaiot.server.enrollment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import com.nexaiot.server.mqtt.management.GeneratedDeviceCredential;

/** AES-256-GCM envelope implementation for the escrow service. */
public final class CredentialEscrowCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CredentialEscrowCipher() {
    }

    static EncryptedCredential encrypt(
            SecretKey key,
            String keyVersion,
            String operationId,
            String deviceId,
            GeneratedDeviceCredential credential
    ) {
        require(key, keyVersion, operationId, deviceId, credential);
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(keyVersion, operationId, deviceId, credential));
            byte[] ciphertext = cipher.doFinal(payload(credential));
            return new EncryptedCredential(
                    Base64.getEncoder().encodeToString(nonce),
                    Base64.getEncoder().encodeToString(ciphertext), keyVersion);
        } catch (Exception exception) {
            throw new IllegalStateException("Credential escrow encryption failed.", exception);
        }
    }

    static GeneratedDeviceCredential decrypt(
            SecretKey key,
            EncryptedCredential encrypted,
            String operationId,
            String deviceId,
            String username,
            String clientId
    ) {
        if (key == null || encrypted == null || operationId == null || operationId.isBlank()
                || deviceId == null || deviceId.isBlank() || username == null || username.isBlank()
                || clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("Credential escrow context is invalid.");
        }
        try {
            byte[] nonce = Base64.getDecoder().decode(encrypted.nonce());
            byte[] ciphertext = Base64.getDecoder().decode(encrypted.ciphertext());
            if (nonce.length != NONCE_BYTES) {
                throw new IllegalArgumentException("Credential escrow nonce is invalid.");
            }
            GeneratedDeviceCredential context = new GeneratedDeviceCredential(
                    username, clientId, "context-only");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(encrypted.keyVersion(), operationId, deviceId, context));
            return readPayload(cipher.doFinal(ciphertext));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Credential escrow decryption failed.", exception);
        }
    }

    private static byte[] payload(GeneratedDeviceCredential credential) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        write(output, credential.username());
        write(output, credential.clientId());
        write(output, credential.password());
        output.flush();
        return bytes.toByteArray();
    }

    private static GeneratedDeviceCredential readPayload(byte[] payload) throws Exception {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
        String username = read(input);
        String clientId = read(input);
        String password = read(input);
        if (input.available() != 0) {
            throw new IllegalArgumentException("Credential escrow payload is invalid.");
        }
        return new GeneratedDeviceCredential(username, clientId, password);
    }

    private static byte[] aad(String keyVersion, String operationId, String deviceId,
            GeneratedDeviceCredential credential) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        write(output, "NexaIoT/CredentialEscrow/v1");
        write(output, keyVersion);
        write(output, operationId);
        write(output, deviceId);
        write(output, credential.username());
        write(output, credential.clientId());
        output.flush();
        return bytes.toByteArray();
    }

    private static void require(SecretKey key, String keyVersion, String operationId,
            String deviceId, GeneratedDeviceCredential credential) {
        if (key == null || keyVersion == null || keyVersion.isBlank()
                || operationId == null || operationId.isBlank()
                || deviceId == null || deviceId.isBlank()
                || credential == null || credential.username() == null
                || credential.username().isBlank() || credential.clientId() == null
                || credential.clientId().isBlank() || credential.password() == null
                || credential.password().isBlank()) {
            throw new IllegalArgumentException("Credential escrow input is invalid.");
        }
    }

    private static void write(DataOutputStream output, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String read(DataInputStream input) throws Exception {
        int length = input.readInt();
        if (length < 1 || length > 4096) {
            throw new IllegalArgumentException("Credential escrow payload is invalid.");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IllegalArgumentException("Credential escrow payload is truncated.");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    record EncryptedCredential(String nonce, String ciphertext, String keyVersion) {
    }
}
