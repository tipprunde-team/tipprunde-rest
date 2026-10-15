package com.mosbach.tipprunde.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mosbach.tipprunde.data.api.AuthManager;
import com.mosbach.tipprunde.error.GlobalExceptionHandler;
import com.mosbach.tipprunde.model.auth.TokenAnswer;

// Standalone-Test mit Hilfs-Controller und Platzhalter-Manager, daher keine Datenbank nötig
class AuthInterceptorTest {

    private static final String VALID_TOKEN = "gueltiges-token";

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
            .addInterceptors(new AuthInterceptor(new FakeAuthManager()))
            .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private ResultActions getWithToken(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    private static void expectUnauthorized(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("Bitte melde dich erneut an."));
    }

    @Test
    void publicEndpointsWorkWithoutToken() throws Exception {
        mockMvc.perform(get("/api/ping")).andExpect(status().isOk());
        mockMvc.perform(post("/api/users")).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth")).andExpect(status().isOk());
        mockMvc.perform(get("/api/invites/abc123")).andExpect(status().isOk());
    }

    @Test
    void sameUrlButProtectedMethodNeedsToken() throws Exception {
        expectUnauthorized(mockMvc.perform(delete("/api/auth")));
        mockMvc.perform(delete("/api/auth").header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        expectUnauthorized(mockMvc.perform(get("/api/users/me")));
        expectUnauthorized(mockMvc.perform(get("/api/groups")));
    }

    @Test
    void changedTokenReturns401() throws Exception {
        expectUnauthorized(getWithToken("/api/users/me", VALID_TOKEN + "x"));
    }

    @Test
    void tokenInUrlIsIgnored() throws Exception {
        expectUnauthorized(mockMvc.perform(get("/api/users/me").param("token", VALID_TOKEN)));
    }

    @Test
    void headerWithoutBearerSchemeReturns401() throws Exception {
        expectUnauthorized(mockMvc.perform(get("/api/users/me").header("Authorization", VALID_TOKEN)));
        expectUnauthorized(mockMvc.perform(get("/api/users/me").header("Authorization", "Basic " + VALID_TOKEN)));
    }

    @Test
    void validTokenPassesUserIdToController() throws Exception {
        getWithToken("/api/users/me", VALID_TOKEN)
                .andExpect(status().isOk())
                .andExpect(content().string("42"));
    }

    @Test
    void bearerSchemeIsCaseInsensitive() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "bearer " + VALID_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void optionsRequestsAlwaysPassForCorsPreflight() throws Exception {
        mockMvc.perform(options("/api/groups")).andExpect(status().isOk());
    }

    @Test
    void userIdNeverComesFromRequestParameters() throws Exception {
        getWithToken("/api/users/me?userId=1", VALID_TOKEN).andExpect(content().string("42"));
    }

    @RestController
    @RequestMapping("/api")
    static class TestController {

        @GetMapping("/ping")
        String ping() {
            return "ok";
        }

        @PostMapping("/users")
        String register() {
            return "ok";
        }

        @PostMapping("/auth")
        String logOn() {
            return "ok";
        }

        @DeleteMapping("/auth")
        String logOff(@CurrentUserId long userId) {
            return "ok";
        }

        @GetMapping("/invites/{code}")
        String invite() {
            return "ok";
        }

        @GetMapping("/users/me")
        String me(@CurrentUserId long userId) {
            return Long.toString(userId);
        }

        @GetMapping("/groups")
        String groups(@CurrentUserId long userId) {
            return "ok";
        }
    }

    // Kennt nur ein gültiges Token, die Nutzer-ID dazu ist 42
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
            return VALID_TOKEN.equals(token) ? Optional.of(42L) : Optional.empty();
        }
    }
}
