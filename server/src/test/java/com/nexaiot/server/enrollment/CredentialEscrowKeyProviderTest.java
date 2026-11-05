package com.nexaiot.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class CredentialEscrowKeyProviderTest {

    @Test
    void validExternalKeyIsDecodedWithVersion() {
        String encoded = Base64.getEncoder().encodeToString(new byte[32]);
        CredentialEscrowKeyProvider provider = new CredentialEscrowKeyProvider(encoded, "key-1");

        assertEquals("AES", provider.currentKey().getAlgorithm());
        assertEquals("key-1", provider.currentKeyVersion());
    }

    @Test
    void missingInvalidOrWrongSizedKeyFailsClosed() {
        assertThrows(IllegalStateException.class,
                () -> new CredentialEscrowKeyProvider("", "key-1").currentKey());
        assertThrows(IllegalStateException.class,
                () -> new CredentialEscrowKeyProvider("not-base64", "key-1").currentKey());
        assertThrows(IllegalStateException.class,
                () -> new CredentialEscrowKeyProvider(
                        Base64.getEncoder().encodeToString(new byte[16]), "key-1").currentKey());
        assertThrows(IllegalStateException.class,
                () -> new CredentialEscrowKeyProvider(
                        Base64.getEncoder().encodeToString(new byte[32]), "").currentKeyVersion());
    }
}
