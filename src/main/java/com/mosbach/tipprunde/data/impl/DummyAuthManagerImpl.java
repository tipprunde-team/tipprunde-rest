package com.mosbach.tipprunde.data.impl;

import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.mosbach.tipprunde.data.api.AuthManager;
import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.model.auth.TokenAnswer;
import com.mosbach.tipprunde.model.user.UserSummary;
import com.mosbach.tipprunde.security.JwtService;
import com.mosbach.tipprunde.security.LogMasking;

// Login gegen einen Testnutzer im Speicher, aktiv bei app.auth.manager=dummy
@Component
@ConditionalOnProperty(name = "app.auth.manager", havingValue = "dummy", matchIfMissing = true)
public class DummyAuthManagerImpl implements AuthManager {

    private static final Logger log = LoggerFactory.getLogger(DummyAuthManagerImpl.class);

    private static final long TEST_USER_ID = 1L;
    private static final String TEST_EMAIL = "test@tippseite.de";
    private static final String TEST_USERNAME = "tester";

    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final String testPasswordHash;

    // PasswordEncoder kommt als Bean aus PasswordConfig
    public DummyAuthManagerImpl(JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.testPasswordHash = passwordEncoder.encode("Test1234!");
    }

    @Override
    public TokenAnswer logOn(String email, String password) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        // Passwort immer prüfen, damit unbekannte E-Mails nicht schneller antworten
        boolean passwordOk = passwordEncoder.matches(password == null ? "" : password, testPasswordHash);
        if (!TEST_EMAIL.equals(normalizedEmail) || !passwordOk) {
            log.warn("Login failed: unknown account or wrong password for {}", LogMasking.maskEmail(normalizedEmail));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "E-Mail oder Passwort ist falsch.");
        }
        String token = jwtService.createToken(TEST_USER_ID, TEST_USERNAME);
        log.info("Login successful: userId={}", TEST_USER_ID);
        return new TokenAnswer(token, jwtService.getExpiresAt(token), new UserSummary(TEST_USER_ID, TEST_USERNAME));
    }

    @Override
    public void logOff(String token) {
        // zustandslos, nichts zu speichern
    }

    @Override
    public Optional<Long> getUserIdFromToken(String token) {
        return jwtService.verify(token);
    }
}