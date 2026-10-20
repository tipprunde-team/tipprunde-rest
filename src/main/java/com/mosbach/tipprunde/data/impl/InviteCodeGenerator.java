package com.mosbach.tipprunde.data.impl;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

// Erzeugt Einladungscodes: 10 Zeichen aus einem URL-sicheren Alphabet ohne leicht verwechselbare Zeichen
@Component
public class InviteCodeGenerator {

    public static final int CODE_LENGTH = 10;

    // ohne 0, O, 1, I, l, damit sich der Code gut abtippen lässt
    static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
