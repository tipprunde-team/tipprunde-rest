package com.mosbach.tipprunde.data.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

import javax.sql.DataSource;

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

// Login gegen die Tabelle users, aktiv bei app.auth.manager=postgres
@Component
@ConditionalOnProperty(name = "app.auth.manager", havingValue = "postgres")
public class PostgresAuthManagerImpl implements AuthManager {

    private static final Logger log = LoggerFactory.getLogger(PostgresAuthManagerImpl.class);

    private static final String SQL_FIND_USER =
            "SELECT id, username, password_hash FROM users WHERE email = ?";

    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    // Vergleichs-Hash für unbekannte E-Mails, damit die Antwortzeit gleich bleibt
    private final String dummyHash;

    public PostgresAuthManagerImpl(DataSource dataSource, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.dataSource = dataSource;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Override
    public TokenAnswer logOn(String email, String password) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String rawPassword = password == null ? "" : password;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SQL_FIND_USER)) {
            statement.setString(1, normalizedEmail);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    passwordEncoder.matches(rawPassword, dummyHash);
                    // Grund nur im Log, die Antwort bleibt für beide Fälle gleich
                    log.warn("Login failed: unknown account {}", LogMasking.maskEmail(normalizedEmail));
                    throw unauthorized();
                }
                long userId = result.getLong("id");
                String username = result.getString("username");
                String passwordHash = result.getString("password_hash");
                if (!passwordEncoder.matches(rawPassword, passwordHash)) {
                    log.warn("Login failed: wrong password, userId={}", userId);
                    throw unauthorized();
                }
                String token = jwtService.createToken(userId, username);
                log.info("Login successful: userId={}", userId);
                return new TokenAnswer(token, jwtService.getExpiresAt(token), new UserSummary(userId, username));
            }
        } catch (SQLException e) {
            // landet im GlobalExceptionHandler als 500 INTERNAL_ERROR
            throw new IllegalStateException("Datenbankfehler beim Login", e);
        }
    }

    @Override
    public void logOff(String token) {
        // zustandslos, Client verwirft das Token
    }

    @Override
    public Optional<Long> getUserIdFromToken(String token) {
        return jwtService.verify(token);
    }

    // Gleiche Antwort für unbekannte E-Mail und falsches Passwort
    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "E-Mail oder Passwort ist falsch.");
    }
}