package com.mosbach.tipprunde.data.impl;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.mosbach.tipprunde.data.api.UserManager;
import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.model.user.RegisterRequest;
import com.mosbach.tipprunde.model.user.UserProfileResponse;
import com.mosbach.tipprunde.model.user.UserResponse;

// Nutzer in der Tabelle users, alle Abfragen mit PreparedStatement
@Component
public class PostgresUserManagerImpl implements UserManager {

    private static final Logger log = LoggerFactory.getLogger(PostgresUserManagerImpl.class);

    // SQLSTATE für Verletzung einer UNIQUE-Regel
    private static final String UNIQUE_VIOLATION = "23505";
    // BCrypt verarbeitet höchstens 72 Bytes (Umlaute zählen doppelt)
    private static final int BCRYPT_MAX_BYTES = 72;

    private static final String INSERT_USER =
            "INSERT INTO users (email, username, password_hash) VALUES (?, ?, ?) RETURNING id";
    private static final String EMAIL_EXISTS = "SELECT 1 FROM users WHERE email = ?";
    private static final String SELECT_PROFILE =
            "SELECT id, email, username, is_system_admin FROM users WHERE id = ?";

    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;

    public PostgresUserManagerImpl(DataSource dataSource, PasswordEncoder passwordEncoder) {
        this.dataSource = dataSource;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Das Passwort ist zu lang. Es darf höchstens 72 Bytes haben, Umlaute und Sonderzeichen zählen mehrfach.");
        }
        String passwordHash = passwordEncoder.encode(request.password());

        try (Connection con = dataSource.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(INSERT_USER)) {
                ps.setString(1, request.email());
                ps.setString(2, request.username());
                ps.setString(3, passwordHash);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    long id = rs.getLong(1);
                    log.info("Nutzer {} registriert", id);
                    return new UserResponse(id, request.email(), request.username());
                }
            } catch (SQLException e) {
                if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                    // greift auch bei gleichzeitiger Registrierung, die Datenbank entscheidet
                    throw duplicate(con, request.email());
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Datenbankfehler bei der Registrierung", e);
        }
    }

    @Override
    public UserProfileResponse getProfile(long userId) {
        try (Connection con = dataSource.getConnection();
                PreparedStatement ps = con.prepareStatement(SELECT_PROFILE)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                            "Nicht angemeldet oder Sitzung abgelaufen.");
                }
                return new UserProfileResponse(rs.getLong("id"), rs.getString("email"),
                        rs.getString("username"), rs.getBoolean("is_system_admin"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Datenbankfehler beim Lesen des Profils", e);
        }
    }

    // Welche der beiden UNIQUE-Regeln hat ausgelöst? Zuerst die E-Mail prüfen
    private ApiException duplicate(Connection con, String email) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(EMAIL_EXISTS)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN",
                            "Diese E-Mail-Adresse ist schon registriert.");
                }
            }
        }
        return new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN",
                "Dieser Benutzername ist schon vergeben.");
    }
}
