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

// Unit test without Spring: create and verify tokens
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
        // Token with the same secret but an expiry in the past
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
        // Attacker puts the payload of another user's token (user 2) in front of their own signature (user 1)
        String[] own = jwtService.createToken(1L, "tester").split("\\.");
        String[] other = jwtService.createToken(2L, "other").split("\\.");
        String forged = own[0] + "." + other[1] + "." + own[2];
        assertTrue(jwtService.verify(forged).isEmpty());
    }

    @Test
    void lastBase64urlCharacterOfSignatureCarriesPaddingBits() {
        // Why the old tampering test was flaky: a 32-byte HMAC-SHA256 signature has 43 Base64url characters,
        // the last one holds 4 data bits and 2 padding bits. 'A' (000000) and 'B' (000001) differ only in a
        // padding bit, so both decode to the same signature (RFC 4648, section 3.5).
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