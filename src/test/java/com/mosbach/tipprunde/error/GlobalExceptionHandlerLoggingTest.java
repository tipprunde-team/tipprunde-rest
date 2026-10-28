package com.mosbach.tipprunde.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

// Prüft, dass abgelehnte Zugriffe im Log stehen und dabei nichts Vertrauliches
class GlobalExceptionHandlerLoggingTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
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

    private List<String> warnings() {
        return appender.list.stream().filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void forbiddenIsLoggedWithStatusCodeMethodAndPath() throws Exception {
        mockMvc.perform(get("/test/forbidden"));

        assertEquals(1, warnings().size());
        String line = warnings().get(0);
        assertTrue(line.contains("status=403"), line);
        assertTrue(line.contains("code=NOT_A_MEMBER"), line);
        assertTrue(line.contains("method=GET"), line);
        assertTrue(line.contains("path=/test/forbidden"), line);
    }

    @Test
    void unauthorizedIsLogged() throws Exception {
        mockMvc.perform(get("/test/unauthorized"));

        assertEquals(1, warnings().size());
        assertTrue(warnings().get(0).contains("status=401"));
        assertTrue(warnings().get(0).contains("code=UNAUTHORIZED"));
    }

    @Test
    void userIdFromTheRequestIsLoggedWhenKnown() throws Exception {
        mockMvc.perform(get("/test/forbidden").requestAttr("userId", 7L));

        assertTrue(warnings().get(0).contains("user=7"), warnings().get(0));
    }

    @Test
    void unknownUserIsLoggedAsDash() throws Exception {
        mockMvc.perform(get("/test/forbidden"));

        assertTrue(warnings().get(0).contains("user=-"), warnings().get(0));
    }

    @Test
    void tokenHeaderAndQueryStringNeverAppearInTheLog() throws Exception {
        mockMvc.perform(get("/test/unauthorized?token=GEHEIM-IN-DER-URL")
                .header("Authorization", "Bearer GEHEIM-IM-HEADER"));

        String all = String.join("\n", warnings());
        assertFalse(all.contains("GEHEIM-IN-DER-URL"), all);
        assertFalse(all.contains("GEHEIM-IM-HEADER"), all);
        assertTrue(all.contains("path=/test/unauthorized"), all);
    }

    @Test
    void conflictAndNotFoundAreNotLoggedAsAccessDenied() throws Exception {
        mockMvc.perform(get("/test/conflict"));
        mockMvc.perform(get("/test/gibtsnicht"));

        assertTrue(warnings().isEmpty(), warnings().toString());
    }

    @RestController
    static class TestController {

        @GetMapping("/test/forbidden")
        String forbidden() {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_A_MEMBER", "Du bist kein Mitglied dieser Tippgruppe.");
        }

        @GetMapping("/test/unauthorized")
        String unauthorized() {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Bitte melde dich erneut an.");
        }

        @GetMapping("/test/conflict")
        String conflict() {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "E-Mail ist schon vergeben.");
        }
    }
}
