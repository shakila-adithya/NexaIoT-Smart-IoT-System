package com.nexaiot.server.enrollment;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

/** Canonical, domain-separated message format for device trust-anchor proof. */
public final class DeviceTrustProtocol {

    public static final String VERSION = "NexaIoT/DeviceTrustAnchor/v1";

    private DeviceTrustProtocol() {
    }

    public static String expectedClientId(String deviceKey) {
        if (deviceKey == null || deviceKey.isBlank()) {
            throw new IllegalArgumentException("Device key is required.");
        }
        return "nexaiot-device-" + deviceKey;
    }

    public static byte[] registrationMessage(
            String deviceKey,
            String enrollmentOperationId,
            String ownerEmail,
            String mqttClientId,
            String challenge,
            String publicKeyFingerprint
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            write(output, VERSION);
            write(output, deviceKey);
            write(output, enrollmentOperationId);
            write(output, ownerEmail);
            write(output, mqttClientId);
            write(output, challenge);
            write(output, publicKeyFingerprint);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Trust-anchor message could not be built.", exception);
        }
    }

    public static String fingerprint(PublicKey publicKey) {
        if (publicKey == null) {
            throw new IllegalArgumentException("Public key is required.");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(publicKey.getEncoded()));
        } catch (Exception exception) {
            throw new IllegalStateException("Public-key fingerprint could not be computed.", exception);
        }
    }

    public static PublicKey decodeP256(String encoded) {
        try {
            byte[] der = java.util.Base64.getDecoder().decode(encoded);
            PublicKey key = KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(der));
            if (!(key instanceof ECPublicKey ecKey)
                    || !sameCurve(ecKey.getParams(), p256Parameters())) {
                throw new IllegalArgumentException("Only P-256 public keys are supported.");
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Trust-anchor public key is invalid.", exception);
        }
    }

    public static byte[] deliveryMessage(
            String deviceKey,
            String enrollmentOperationId,
            String ownerEmail,
            String mqttClientId,
            String trustAnchorFingerprint,
            String challenge,
            String challengeExpiresAt
    ) {
        return fields("NexaIoT/CredentialDelivery/v1", deviceKey, enrollmentOperationId,
                ownerEmail, mqttClientId, trustAnchorFingerprint, challenge, challengeExpiresAt);
    }

    public static byte[] activationMessage(
            String deviceKey,
            String enrollmentOperationId,
            String ownerEmail,
            String mqttClientId,
            String trustAnchorFingerprint,
            String challenge,
            String challengeExpiresAt
    ) {
        return fields("NexaIoT/Activation/v1", deviceKey, enrollmentOperationId,
                ownerEmail, mqttClientId, trustAnchorFingerprint, challenge,
                challengeExpiresAt);
    }

    private static byte[] fields(String... values) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            for (String value : values) {
                write(output, value);
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Trust protocol message could not be built.", exception);
        }
    }

    private static ECParameterSpec p256Parameters() throws Exception {
        java.security.AlgorithmParameters parameters =
                java.security.AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private static boolean sameCurve(ECParameterSpec actual, ECParameterSpec expected) {
        return actual != null && expected != null
                && java.util.Objects.equals(actual.getCurve(), expected.getCurve())
                && java.util.Objects.equals(actual.getGenerator(), expected.getGenerator())
                && actual.getOrder().equals(expected.getOrder())
                && actual.getCofactor() == expected.getCofactor();
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Trust-anchor message fields are required.");
        }
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
