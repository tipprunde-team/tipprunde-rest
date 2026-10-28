package com.mosbach.tipprunde.data.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.model.auth.TokenAnswer;
import com.mosbach.tipprunde.security.JwtService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

// Login-Logs: E-Mail nur maskiert, nie Passwort oder Token
class DummyAuthManagerImplLoggingTest {

    private final DummyAuthManagerImpl manager = new DummyAuthManagerImpl(
            new JwtService("0123456789abcdef0123456789abcdef0123", 24), new BCryptPasswordEncoder(4));
    private final Logger logger = (Logger) LoggerFactory.getLogger(DummyAuthManagerImpl.class);
    private ListAppender<ILoggingEvent> appender;

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

    @Test
    void failedLoginIsLoggedWithMaskedEmailOnly() {
        assertThrows(ApiException.class, () -> manager.logOn("Anna.Muster@Example.de", "falsches-passwort"));

        String log = log();
        assertTrue(log.contains("WARN Login failed"), log);
        assertTrue(log.contains("a***@example.de"), log);
        assertFalse(log.contains("anna.muster"), log);
        assertFalse(log.contains("falsches-passwort"), log);
    }

    @Test
    void successfulLoginIsLoggedWithUserIdOnly() {
        TokenAnswer answer = manager.logOn("test@tippseite.de", "Test1234!");

        String log = log();
        assertTrue(log.contains("INFO Login successful: userId=1"), log);
        assertFalse(log.contains("test@tippseite.de"), log);
        assertFalse(log.contains("Test1234!"), log);
        assertFalse(log.contains(answer.token()), log);
        assertEquals(1, appender.list.size());
    }
}
