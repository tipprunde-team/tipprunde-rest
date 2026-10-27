package com.mosbach.tipprunde.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

// Unit-Test ohne Spring: Token erstellen und prüfen
class JwtServiceTest {

    private static final String SECRET = "test-secret-with-at-least-32-characters-1234";
    private static final String OTHER_SECRET = "other-secret-with-at-least-32-characters-99";

    private final JwtService jwtService = new JwtService(SECRET, 24);

    @Test
    void validTokenReturnsUserId() {
        String token = jwtService.createToken(42L, "tester");
        assertEquals(Optional.of(42L), jwtService.verify(token));
    }

    @Test
    void expiresAtIsAbout24HoursAhead() {
        String token = jwtService.createToken(1L, "tester");
        Duration remaining = Duration.between(Instant.now(), jwtService.getExpiresAt(token));
        assertTrue(remaining.toHours() >= 23 && remaining.toHours() <= 24);
    }

    @Test
    void expiredTokenIsRejected() {
        // Token mit gleichem Secret, aber Ablauf in der Vergangenheit
        Instant past = Instant.now().minus(Duration.ofHours(2));
        String token = JWT.create()
                .withIssuer("tipprunde")
                .withSubject("1")
                .withIssuedAt(past.minus(Duration.ofHours(1)))
                .withExpiresAt(past)
                .sign(Algorithm.HMAC256(SECRET));
        assertTrue(jwtService.verify(token).isEmpty());
    }

    @Test
    void tamperedTokenIsRejected() {
        // Angreifer setzt den Inhalt eines fremden Tokens (Nutzer 2) vor die eigene Signatur (Nutzer 1)
        String[] own = jwtService.createToken(1L, "tester").split("\\.");
        String[] other = jwtService.createToken(2L, "other").split("\\.");
        String forged = own[0] + "." + other[1] + "." + own[2];
        assertTrue(jwtService.verify(forged).isEmpty());
    }

    @Test
    void lastBase64urlCharacterOfSignatureCarriesPaddingBits() {
        // Warum der alte Manipulations-Test manchmal fehlschlug: Die HMAC-SHA256-Signatur hat 32 Byte, also 43
        // Base64url-Zeichen. Das letzte Zeichen trägt nur 4 Datenbits und 2 Füllbits. 'A' (000000) und 'B' (000001)
        // unterscheiden sich nur in einem Füllbit, beide ergeben dieselbe Signatur (RFC 4648, Abschnitt 3.5).
        String signature = "poHMhU9KKGY0_ymIjxqlzsLJVWN47ReOPcUHJU_368A";
        String changed = signature.substring(0, signature.length() - 1) + "B";
        assertArrayEquals(Base64.getUrlDecoder().decode(signature), Base64.getUrlDecoder().decode(changed));
    }

    @Test
    void tokenWithOtherSecretIsRejected() {
        String foreignToken = new JwtService(OTHER_SECRET, 24).createToken(1L, "tester");
        assertTrue(jwtService.verify(foreignToken).isEmpty());
    }

    @Test
    void missingOrEmptyTokenIsRejected() {
        assertTrue(jwtService.verify(null).isEmpty());
        assertTrue(jwtService.verify("").isEmpty());
        assertTrue(jwtService.verify("not-a-jwt").isEmpty());
    }

    @Test
    void tooShortSecretStopsStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 24));
    }
}