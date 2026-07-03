package com.example.demo.service.llm;

import com.example.demo.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class SimpleLlmSecretCrypto implements LlmSecretCrypto {

    private final String secret;

    public SimpleLlmSecretCrypto(@Value("${llm.secret-key:CHANGE_ME_LLM_SECRET}") String secret) {
        this.secret = secret;
    }

    @Override
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        byte[] input = plainText.getBytes(StandardCharsets.UTF_8);
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        byte[] output = xor(input, key);
        return Base64.getEncoder().encodeToString(output);
    }

    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            byte[] key = secret.getBytes(StandardCharsets.UTF_8);
            return new String(xor(decoded, key), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(500, "密钥解密失败", "invalid encrypted api key", e);
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

    private byte[] xor(byte[] input, byte[] key) {
        byte[] output = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (byte) (input[i] ^ key[i % key.length]);
        }
        return output;
    }
}
