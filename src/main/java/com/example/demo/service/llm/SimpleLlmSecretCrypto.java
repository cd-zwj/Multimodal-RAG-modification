package com.example.demo.service.llm;

import com.example.demo.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class SimpleLlmSecretCrypto implements LlmSecretCrypto {

    private static final String CIPHER_PREFIX = "v1:";
    private static final String DEFAULT_SECRET = "CHANGE_ME_LLM_SECRET";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec keySpec;
    private final byte[] legacyKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public SimpleLlmSecretCrypto(@Value("${llm.secret-key:}") String secret) {
        String normalizedSecret = validateSecret(secret);
        this.legacyKey = normalizedSecret.getBytes(StandardCharsets.UTF_8);
        this.keySpec = new SecretKeySpec(deriveKey(normalizedSecret), "AES");
    }

    @Override
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
            return CIPHER_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new BusinessException(500, "密钥加密失败", "llm api key encryption failed", e);
        }
    }

    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        if (cipherText.startsWith(CIPHER_PREFIX)) {
            return decryptAesGcm(cipherText.substring(CIPHER_PREFIX.length()));
        }
        return decryptLegacyXor(cipherText);
    }

    private String decryptAesGcm(String encodedPayload) {
        try {
            byte[] payload = Base64.getDecoder().decode(encodedPayload);
            if (payload.length <= GCM_IV_BYTES) {
                throw new BusinessException(500, "密钥解密失败", "encrypted api key payload is too short");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, GCM_IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, GCM_IV_BYTES, payload.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new BusinessException(500, "密钥解密失败", "invalid llm api key secret", e);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(500, "密钥解密失败", "invalid encrypted api key", e);
        } catch (GeneralSecurityException e) {
            throw new BusinessException(500, "密钥解密失败", "llm api key decryption failed", e);
        }
    }

    private String decryptLegacyXor(String cipherText) {
        try {
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            return new String(xor(decoded, legacyKey), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(500, "密钥解密失败", "invalid legacy encrypted api key", e);
        }
    }

    @Override
    public String mask(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return "";
        }
        if (plainText.length() <= 8) {
            return "****";
        }
        return plainText.substring(0, 4) + "****" + plainText.substring(plainText.length() - 4);
    }

    private static String validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("llm.secret-key must be configured from LLM_SECRET_KEY or another secret source");
        }
        String normalized = secret.trim();
        if (DEFAULT_SECRET.equals(normalized)) {
            throw new IllegalStateException("llm.secret-key must not use the default placeholder value");
        }
        if (normalized.length() < 32) {
            throw new IllegalStateException("llm.secret-key must be at least 32 characters long");
        }
        return normalized;
    }

    private static byte[] deriveKey(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private byte[] xor(byte[] input, byte[] key) {
        byte[] output = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (byte) (input[i] ^ key[i % key.length]);
        }
        return output;
    }
}
