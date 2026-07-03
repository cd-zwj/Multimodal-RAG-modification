package com.example.demo.service.llm;

public interface LlmSecretCrypto {

    String encrypt(String plainText);

    String decrypt(String cipherText);

    String mask(String plainText);
}
