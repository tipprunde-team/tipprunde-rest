package com.mosbach.tipprunde.security;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

// Erstellt und prüft JWTs (HS256), Secret aus JWT_SECRET
@Service
public class JwtService {

    private static final String ISSUER = "tipprunde";
    private static final String CLAIM_USERNAME = "username";

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final Duration validity;

    public JwtService(@Value("${app.jwt.secret}") String secret,
            @Value("${app.token.validity-hours}") long validityHours) {
        // Start abbrechen bei fehlendem oder zu kurzem Secret
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET fehlt oder ist kürzer als 32 Zeichen");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).withIssuer(ISSUER).build();
        this.validity = Duration.ofHours(validityHours);
    }

    public String createToken(long userId, String username) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(Long.toString(userId))
                .withClaim(CLAIM_USERNAME, username)
                .withIssuedAt(now)
                .withExpiresAt(now.plus(validity))
                .sign(algorithm);
    }

    // Leer bei abgelaufenem, manipuliertem oder fremd signiertem Token
    public Optional<Long> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            DecodedJWT jwt = verifier.verify(token);
            return Optional.of(Long.parseLong(jwt.getSubject()));
        } catch (JWTVerificationException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    // Ablaufzeit eines selbst erstellten Tokens
    public Instant getExpiresAt(String token) {
        return JWT.decode(token).getExpiresAtAsInstant();
    }
}