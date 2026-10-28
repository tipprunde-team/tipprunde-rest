package com.mosbach.tipprunde.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.mosbach.tipprunde.data.api.AuthManager;
import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.error.GlobalExceptionHandler;
import com.mosbach.tipprunde.model.auth.TokenAnswer;
import com.mosbach.tipprunde.model.user.UserSummary;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

// Standalone-Test mit Platzhalter-Manager, daher keine Datenbank nötig
class AuthControllerTest {

    private static final String VALID_TOKEN = "gueltiges-token";

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(new FakeAuthManager()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private ResultActions login(String json) throws Exception {
        return mockMvc.perform(post("/api/auth").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void loginWithCorrectDataReturnsToken() throws Exception {
        login("{\"email\":\"test@tippseite.de\",\"password\":\"Test1234!\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(VALID_TOKEN))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.username").value("tester"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        login("{\"email\":\"test@tippseite.de\",\"password\":\"falsch123\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void emptyFieldsReturnValidationError() throws Exception {
        login("{\"email\":\"\",\"password\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void missingBodyReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/auth").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void logoutWithValidTokenReturns204() throws Exception {
        mockMvc.perform(delete("/api/auth").header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutWithoutHeaderReturns401() throws Exception {
        mockMvc.perform(delete("/api/auth"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void logoutWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(delete("/api/auth").header("Authorization", "Bearer manipuliert"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void controllerNeverLogsTheEmailOrThePassword() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(AuthController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            login("{\"email\":\"test@tippseite.de\",\"password\":\"falsch123\"}");
            login("{\"email\":\"test@tippseite.de\",\"password\":\"Test1234!\"}");
        } finally {
            logger.detachAppender(appender);
        }

        String all = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertFalse(all.contains("test@tippseite.de"), all);
        assertFalse(all.contains("falsch123"), all);
        assertFalse(all.contains("Test1234!"), all);
    }

    // Platzhalter statt Datenbank: kennt genau einen Nutzer und ein Token
    private static class FakeAuthManager implements AuthManager {

        @Override
        public TokenAnswer logOn(String email, String password) {
            if ("test@tippseite.de".equals(email) && "Test1234!".equals(password)) {
                return new TokenAnswer(VALID_TOKEN, Instant.parse("2030-01-01T00:00:00Z"), new UserSummary(1L, "tester"));
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "E-Mail oder Passwort ist falsch.");
        }

        @Override
        public void logOff(String token) {
        }

        @Override
        public Optional<Long> getUserIdFromToken(String token) {
            return VALID_TOKEN.equals(token) ? Optional.of(1L) : Optional.empty();
        }
    }
}