package com.mosbach.tipprunde.data.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.security.JwtService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

// Login gegen eine gemockte Datenbank: Log enthält Grund und IDs, aber nie E-Mail, Passwort oder Token
class PostgresAuthManagerImplLoggingTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final DataSource dataSource = mock(DataSource.class);
    private final ResultSet resultSet = mock(ResultSet.class);
    private final PostgresAuthManagerImpl manager;
    private final Logger logger = (Logger) LoggerFactory.getLogger(PostgresAuthManagerImpl.class);
    private ListAppender<ILoggingEvent> appender;

    PostgresAuthManagerImplLoggingTest() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        manager = new PostgresAuthManagerImpl(dataSource, encoder,
                new JwtService("0123456789abcdef0123456789abcdef0123", 24));
    }

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private String log() {
        List<String> lines = appender.list.stream().map(e -> e.getLevel() + " " + e.getFormattedMessage()).toList();
        return String.join("\n", lines);
    }

    private void accountExists() throws SQLException {
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("id")).thenReturn(7L);
        when(resultSet.getString("username")).thenReturn("anna");
        when(resultSet.getString("password_hash")).thenReturn(encoder.encode("Geheim123"));
    }

    @Test
    void unknownAccountIsLoggedWithMaskedEmail() throws SQLException {
        when(resultSet.next()).thenReturn(false);

        assertThrows(ApiException.class, () -> manager.logOn("Anna.Muster@Example.de", "Geheim123"));

        String log = log();
        assertTrue(log.contains("WARN Login failed: unknown account a***@example.de"), log);
        assertFalse(log.contains("anna.muster"), log);
        assertFalse(log.contains("Geheim123"), log);
    }

    @Test
    void wrongPasswordIsLoggedWithUserIdOnly() throws SQLException {
        accountExists();

        assertThrows(ApiException.class, () -> manager.logOn("anna@example.de", "falsches-passwort"));

        String log = log();
        assertTrue(log.contains("WARN Login failed: wrong password, userId=7"), log);
        assertFalse(log.contains("anna@example.de"), log);
        assertFalse(log.contains("falsches-passwort"), log);
    }

    @Test
    void successfulLoginIsLoggedWithoutEmailPasswordOrToken() throws SQLException {
        accountExists();

        var answer = manager.logOn("anna@example.de", "Geheim123");

        String log = log();
        assertTrue(log.contains("INFO Login successful: userId=7"), log);
        assertFalse(log.contains("anna@example.de"), log);
        assertFalse(log.contains("Geheim123"), log);
        assertFalse(log.contains(answer.token()), log);
    }
}
