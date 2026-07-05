package com.example.demo.service.llm;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleLlmSecretCryptoTest {

    private static final String SECRET = "test-llm-secret-key-with-32-plus-chars";

    @Test
    void shouldEncryptAndDecryptWithAesGcm() {
        SimpleLlmSecretCrypto crypto = new SimpleLlmSecretCrypto(SECRET);

        String cipherText = crypto.encrypt("sk-test-secret");

        assertTrue(cipherText.startsWith("v1:"));
        assertNotEquals("sk-test-secret", cipherText);
        assertEquals("sk-test-secret", crypto.decrypt(cipherText));
    }

    @Test
    void shouldRejectMissingWeakOrDefaultSecret() {
        assertThrows(IllegalStateException.class, () -> new SimpleLlmSecretCrypto(""));
        assertThrows(IllegalStateException.class, () -> new SimpleLlmSecretCrypto("CHANGE_ME_LLM_SECRET"));
        assertThrows(IllegalStateException.class, () -> new SimpleLlmSecretCrypto("short-secret"));
    }

    @Test
    void shouldDecryptLegacyXorCiphertextForExistingRows() {
        String plainText = "sk-legacy";
        byte[] input = plainText.getBytes(StandardCharsets.UTF_8);
        byte[] key = SECRET.getBytes(StandardCharsets.UTF_8);
        byte[] output = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (byte) (input[i] ^ key[i % key.length]);
        }
        String legacyCipherText = Base64.getEncoder().encodeToString(output);

        SimpleLlmSecretCrypto crypto = new SimpleLlmSecretCrypto(SECRET);

        assertEquals(plainText, crypto.decrypt(legacyCipherText));
    }
}
