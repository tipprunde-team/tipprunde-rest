package com.mosbach.tipprunde.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.mosbach.tipprunde.data.api.AuthManager;
import com.mosbach.tipprunde.data.api.UserManager;
import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.error.GlobalExceptionHandler;
import com.mosbach.tipprunde.model.auth.TokenAnswer;
import com.mosbach.tipprunde.model.user.RegisterRequest;
import com.mosbach.tipprunde.model.user.UserProfileResponse;
import com.mosbach.tipprunde.model.user.UserResponse;
import com.mosbach.tipprunde.security.AuthInterceptor;
import com.mosbach.tipprunde.security.CurrentUserIdArgumentResolver;

// Standalone-Test mit Platzhalter-Manager, daher keine Datenbank nötig
class UserControllerTest {

    private final FakeUserManager userManager = new FakeUserManager();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userManager))
            .addInterceptors(new AuthInterceptor(new FakeAuthManager()))
            .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private static String body(String email, String username, String password) {
        return "{\"email\":\"" + email + "\",\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private org.springframework.test.web.servlet.ResultActions register(String json) throws Exception {
        return mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void validRequestReturns201WithoutPassword() throws Exception {
        register(body("anna@example.de", "anna_1", "Geheim123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("anna@example.de"))
                .andExpect(jsonPath("$.username").value("anna_1"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void emailIsTrimmedAndLowercasedBeforeItReachesTheManager() throws Exception {
        register(body("  Anna@Example.DE ", "anna", "Geheim123")).andExpect(status().isCreated());
        assertEquals("anna@example.de", userManager.lastRequest.email());
    }

    @Test
    void invalidEmailReturnsValidationError() throws Exception {
        register(body("keine-mail", "anna", "Geheim123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Die E-Mail-Adresse ist ungültig."));
    }

    @Test
    void passwordWithSevenCharactersIsRejected() throws Exception {
        register(body("anna@example.de", "anna", "1234567"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Das Passwort muss 8 bis 72 Zeichen lang sein."));
    }

    @Test
    void passwordWith73CharactersIsRejected() throws Exception {
        register(body("anna@example.de", "anna", "a".repeat(73)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void usernameWithForbiddenCharactersIsRejected() throws Exception {
        register(body("anna@example.de", "an na!", "Geheim123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void tooShortUsernameIsRejected() throws Exception {
        register(body("anna@example.de", "ab", "Geheim123")).andExpect(status().isBadRequest());
    }

    @Test
    void emailLongerThan254CharactersIsRejected() throws Exception {
        register(body("a".repeat(250) + "@b.de", "anna", "Geheim123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void missingFieldsReturnValidationError() throws Exception {
        register("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void brokenJsonAndEmptyBodyReturnValidationError() throws Exception {
        register("{kaputt").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void conflictFromManagerBecomes409() throws Exception {
        userManager.failWith = new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Diese E-Mail-Adresse ist schon registriert.");
        register(body("anna@example.de", "anna", "Geheim123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"));
    }

    @Test
    void meReturnsOwnProfileForValidToken() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer gueltiges-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("anna@example.de"))
                .andExpect(jsonPath("$.username").value("anna"))
                .andExpect(jsonPath("$.isSystemAdmin").value(false))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void meWithChangedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer gueltiges-tokenx"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // Merkt sich die letzte Anfrage und wirft auf Wunsch einen Fehler
    private static class FakeUserManager implements UserManager {

        RegisterRequest lastRequest;
        ApiException failWith;

        @Override
        public UserResponse register(RegisterRequest request) {
            lastRequest = request;
            if (failWith != null) {
                throw failWith;
            }
            return new UserResponse(1, request.email(), request.username());
        }

        @Override
        public UserProfileResponse getProfile(long userId) {
            return new UserProfileResponse(userId, "anna@example.de", "anna", false);
        }
    }

    // Kennt nur ein gültiges Token, die Nutzer-ID dazu ist 7
    private static class FakeAuthManager implements AuthManager {

        @Override
        public TokenAnswer logOn(String email, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void logOff(String token) {
        }

        @Override
        public Optional<Long> getUserIdFromToken(String token) {
            return "gueltiges-token".equals(token) ? Optional.of(7L) : Optional.empty();
        }
    }
}
