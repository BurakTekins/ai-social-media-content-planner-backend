package com.globalcodelabs.socialmediaplanner.application.port.out.security;

public interface CredentialCipher {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
