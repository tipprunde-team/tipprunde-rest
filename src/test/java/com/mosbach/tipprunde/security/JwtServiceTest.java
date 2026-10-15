package com.mosbach.tipprunde.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

// Unit-Test ohne Spring: Token erstellen und prüfen
class JwtServiceTest {

    private static final String SECRET = "test-secret-mit-mindestens-32-zeichen-1234";
    private static final String OTHER_SECRET = "anderes-secret-mit-mindestens-32-zeichen-99";

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
        String token = jwtService.createToken(1L, "tester");
        // letztes Zeichen der Signatur verändern
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
        assertTrue(jwtService.verify(tampered).isEmpty());
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
        assertTrue(jwtService.verify("kein-jwt").isEmpty());
    }

    @Test
    void tooShortSecretStopsStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtService("zu-kurz", 24));
    }
}