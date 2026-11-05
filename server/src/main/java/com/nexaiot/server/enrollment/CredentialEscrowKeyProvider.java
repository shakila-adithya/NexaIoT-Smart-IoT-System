package com.nexaiot.server.enrollment;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Reads a durable escrow key from protected external configuration. */
@Service
public class CredentialEscrowKeyProvider {

    private final String encodedKey;
    private final String keyVersion;
    private final String encodedKeyRing;

    public CredentialEscrowKeyProvider(
            @Value("${provisioning.credential-escrow.key:}") String encodedKey,
            @Value("${provisioning.credential-escrow.key-version:}") String keyVersion
    ) {
        this(encodedKey, keyVersion, "");
    }

    @Autowired
    public CredentialEscrowKeyProvider(
            @Value("${provisioning.credential-escrow.key:}") String encodedKey,
            @Value("${provisioning.credential-escrow.key-version:}") String keyVersion,
            @Value("${provisioning.credential-escrow.keys:}") String encodedKeyRing
    ) {
        this.encodedKey = encodedKey;
        this.keyVersion = keyVersion;
        this.encodedKeyRing = encodedKeyRing;
    }

    public SecretKey currentKey() {
        return keyFor(currentKeyVersion());
    }

    /** Resolves current or retained historical key material from external configuration. */
    public SecretKey keyFor(String requestedVersion) {
        if (requestedVersion == null || requestedVersion.isBlank()) {
            throw new IllegalStateException("Credential escrow key version is unavailable.");
        }
        String encoded = requestedVersion.trim().equals(keyVersion == null ? "" : keyVersion.trim())
                ? encodedKey : ringValue(requestedVersion.trim());
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("Credential escrow key version is unavailable.");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded.trim());
            if (decoded.length != 32) {
                throw new IllegalStateException("Credential escrow key must be 256 bits.");
            }
            return new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Credential escrow key configuration is invalid.", exception);
        }
    }

    public String currentKeyVersion() {
        if (keyVersion == null || keyVersion.isBlank()) {
            throw new IllegalStateException("Credential escrow key version is unavailable.");
        }
        return keyVersion.trim();
    }

    private String ringValue(String requestedVersion) {
        if (encodedKeyRing == null || encodedKeyRing.isBlank()) {
            return null;
        }
        for (String entry : encodedKeyRing.split(",")) {
            String[] parts = entry.trim().split("=", 2);
            if (parts.length == 2 && requestedVersion.equals(parts[0].trim())) {
                return parts[1].trim();
            }
        }
        return null;
    }
}
