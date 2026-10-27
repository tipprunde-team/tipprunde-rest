package com.mosbach.tipprunde.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LogMaskingTest {

    @Test
    void emailKeepsOnlyFirstLetterAndDomain() {
        assertEquals("a***@example.de", LogMasking.maskEmail("anna@example.de"));
    }

    @Test
    void emailIsTrimmedBeforeMasking() {
        assertEquals("a***@example.de", LogMasking.maskEmail("  anna@example.de "));
    }

    @Test
    void valueWithoutAtSignIsFullyMasked() {
        assertEquals("***", LogMasking.maskEmail("kein-email-format"));
        assertEquals("***", LogMasking.maskEmail("@example.de"));
    }

    @Test
    void emptyValuesAreMarked() {
        assertEquals("(empty)", LogMasking.maskEmail(null));
        assertEquals("(empty)", LogMasking.maskEmail("   "));
    }
}
