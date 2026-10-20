package com.mosbach.tipprunde.data.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class InviteCodeGeneratorTest {

    private final InviteCodeGenerator generator = new InviteCodeGenerator();

    @Test
    void codeHasTenCharacters() {
        assertEquals(10, generator.generate().length());
    }

    @Test
    void codeUsesOnlyTheAllowedAlphabet() {
        for (int i = 0; i < 1000; i++) {
            for (char c : generator.generate().toCharArray()) {
                assertTrue(InviteCodeGenerator.ALPHABET.indexOf(c) >= 0, "Unerlaubtes Zeichen: " + c);
            }
        }
    }

    @Test
    void alphabetIsUrlSafeAndWithoutLookAlikes() {
        assertTrue(InviteCodeGenerator.ALPHABET.matches("[A-Za-z0-9]+"));
        for (char c : "01OIl".toCharArray()) {
            assertTrue(InviteCodeGenerator.ALPHABET.indexOf(c) < 0, "Verwechselbares Zeichen: " + c);
        }
    }

    @Test
    void noRepetitionInThousandRuns() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(generator.generate());
        }
        assertEquals(1000, codes.size());
    }
}
