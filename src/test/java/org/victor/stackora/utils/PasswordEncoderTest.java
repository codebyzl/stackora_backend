package org.victor.stackora.utils;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class PasswordEncoderTest {

    private final PasswordEncoder passwordEncoder =
            PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void encodePasswordShouldGenerateVerifiableHash() {
        String rawPassword = "Password123";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertNotEquals(rawPassword, encodedPassword);
        assertTrue(passwordEncoder.matches(rawPassword, encodedPassword));
        assertFalse(passwordEncoder.matches("WrongPassword", encodedPassword));
        assertTrue(encodedPassword.length() <= 255);
    }
}