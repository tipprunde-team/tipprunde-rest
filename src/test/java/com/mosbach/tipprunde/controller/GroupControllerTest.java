package com.mosbach.tipprunde.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.mosbach.tipprunde.data.api.AuthManager;
import com.mosbach.tipprunde.data.api.GroupManager;
import com.mosbach.tipprunde.error.GlobalExceptionHandler;
import com.mosbach.tipprunde.model.auth.TokenAnswer;
import com.mosbach.tipprunde.model.group.CreateGroupRequest;
import com.mosbach.tipprunde.model.group.GroupCreated;
import com.mosbach.tipprunde.model.group.GroupSummary;
import com.mosbach.tipprunde.security.AuthInterceptor;
import com.mosbach.tipprunde.security.CurrentUserIdArgumentResolver;

// Standalone-Test mit Platzhalter-Manager, daher keine Datenbank nötig
class GroupControllerTest {

    private static final String TOKEN = "Bearer gueltiges-token";

    private final FakeGroupManager groupManager = new FakeGroupManager();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new GroupController(groupManager))
            .addInterceptors(new AuthInterceptor(new FakeAuthManager()))
            .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private ResultActions create(String json) throws Exception {
        return mockMvc.perform(post("/api/groups").header("Authorization", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void createReturns201WithCodeAndAdminRole() throws Exception {
        create("{\"name\":\"WG Test\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("WG Test"))
                .andExpect(jsonPath("$.inviteCode").value("k7Qp2xZ9aB"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void userIdComesFromTheTokenNotFromTheBody() throws Exception {
        create("{\"name\":\"WG Test\",\"userId\":99}").andExpect(status().isCreated());

        assertEquals(7L, groupManager.lastUserId);
    }

    @Test
    void nameIsTrimmedBeforeItReachesTheManager() throws Exception {
        create("{\"name\":\"  WG Test  \"}").andExpect(status().isCreated());

        assertEquals("WG Test", groupManager.lastRequest.name());
    }

    @Test
    void nameWith50CharactersIsAccepted() throws Exception {
        create("{\"name\":\"" + "a".repeat(50) + "\"}").andExpect(status().isCreated());
    }

    @Test
    void emptyNameReturnsValidationError() throws Exception {
        create("{\"name\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Bitte einen Namen für die Tippgruppe angeben."));
        assertEquals(0, groupManager.createCalls);
    }

    @Test
    void blankNameReturnsValidationError() throws Exception {
        create("{\"name\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nameWith51CharactersReturnsValidationError() throws Exception {
        create("{\"name\":\"" + "a".repeat(51) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message")
                        .value("Der Name der Tippgruppe darf höchstens 50 Zeichen lang sein."));
        assertEquals(0, groupManager.createCalls);
    }

    @Test
    void missingNameAndBrokenJsonReturnValidationError() throws Exception {
        create("{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        create("{kaputt").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"WG Test\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        assertEquals(0, groupManager.createCalls);
    }

    @Test
    void listReturnsOwnGroupsWithMemberCountRankAndPoints() throws Exception {
        mockMvc.perform(get("/api/groups").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].name").value("WG Lohrtalweg"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].memberCount").value(4))
                .andExpect(jsonPath("$[0].myRank").value(2))
                .andExpect(jsonPath("$[0].myPoints").value(31));
        assertEquals(7L, groupManager.lastUserId);
    }

    @Test
    void listWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void listWithoutGroupsReturnsEmptyArray() throws Exception {
        groupManager.groups = List.of();

        mockMvc.perform(get("/api/groups").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // Merkt sich die letzten Aufrufe
    private static class FakeGroupManager implements GroupManager {

        long lastUserId;
        CreateGroupRequest lastRequest;
        int createCalls;
        List<GroupSummary> groups = List.of(new GroupSummary(3, "WG Lohrtalweg", "ADMIN", 4, 2, 31));

        @Override
        public GroupCreated createGroup(long userId, CreateGroupRequest request) {
            createCalls++;
            lastUserId = userId;
            lastRequest = request;
            return new GroupCreated(3, request.name(), "k7Qp2xZ9aB", "ADMIN");
        }

        @Override
        public List<GroupSummary> listGroups(long userId) {
            lastUserId = userId;
            return groups;
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
