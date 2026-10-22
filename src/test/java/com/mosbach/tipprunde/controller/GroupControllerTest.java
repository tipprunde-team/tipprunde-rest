package com.mosbach.tipprunde.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.mosbach.tipprunde.data.api.AuthManager;
import com.mosbach.tipprunde.data.api.GroupManager;
import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.error.GlobalExceptionHandler;
import com.mosbach.tipprunde.model.auth.TokenAnswer;
import com.mosbach.tipprunde.model.group.CreateGroupRequest;
import com.mosbach.tipprunde.model.group.GroupCreated;
import com.mosbach.tipprunde.model.group.GroupDetails;
import com.mosbach.tipprunde.model.group.GroupSummary;
import com.mosbach.tipprunde.model.group.InvitePreview;
import com.mosbach.tipprunde.model.group.Member;
import com.mosbach.tipprunde.security.AuthInterceptor;
import com.mosbach.tipprunde.security.CurrentUserIdArgumentResolver;
import com.mosbach.tipprunde.security.GroupAccess;
import com.mosbach.tipprunde.security.Membership;

// Standalone-Test mit Platzhalter-Manager, daher keine Datenbank nötig
class GroupControllerTest {

    private static final String TOKEN = "Bearer gueltiges-token";

    private static final Instant JOINED = Instant.parse("2026-10-19T18:00:00Z");

    private final FakeGroupManager groupManager = new FakeGroupManager();
    private final FakeGroupAccess groupAccess = new FakeGroupAccess();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new GroupController(groupManager, groupAccess))
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

    @Test
    void detailsForAdminContainMembersAndInviteCode() throws Exception {
        groupAccess.role = "ADMIN";

        mockMvc.perform(get("/api/groups/3").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("WG Lohrtalweg"))
                .andExpect(jsonPath("$.inviteCode").value("k7Qp2xZ9aB"))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].userId").value(7))
                .andExpect(jsonPath("$.members[0].username").value("niklas"))
                .andExpect(jsonPath("$.members[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.members[0].joinedAt").value("2026-10-19T18:00:00Z"))
                .andExpect(jsonPath("$.members[1].role").value("MEMBER"));
        assertEquals(3L, groupAccess.lastGroupId);
        assertEquals(7L, groupAccess.lastUserId);
    }

    @Test
    void detailsForNormalMemberHaveNoInviteCodeField() throws Exception {
        groupAccess.role = "MEMBER";

        mockMvc.perform(get("/api/groups/3").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("WG Lohrtalweg"))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.inviteCode").doesNotExist());
    }

    @Test
    void detailsOfForeignGroupReturn403NotAMember() throws Exception {
        groupAccess.failWith = new ApiException(HttpStatus.FORBIDDEN, "NOT_A_MEMBER", "Du bist kein Mitglied dieser Tippgruppe.");

        mockMvc.perform(get("/api/groups/3").header("Authorization", TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_MEMBER"));
        assertEquals(0, groupManager.detailsCalls);
    }

    @Test
    void detailsOfUnknownGroupReturn404GroupNotFound() throws Exception {
        groupAccess.failWith = new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Diese Tippgruppe gibt es nicht.");

        mockMvc.perform(get("/api/groups/999").header("Authorization", TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void detailsWithoutTokenReturn401() throws Exception {
        mockMvc.perform(get("/api/groups/3"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        assertEquals(0, groupManager.detailsCalls);
    }

    @Test
    void detailsWithNonNumericGroupIdReturnValidationError() throws Exception {
        mockMvc.perform(get("/api/groups/abc").header("Authorization", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private ResultActions join(String json) throws Exception {
        return mockMvc.perform(post("/api/groups/join").header("Authorization", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void joinReturns200WithGroupSummaryAsMember() throws Exception {
        join("{\"inviteCode\":\"k7Qp2xZ9aB\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("WG Lohrtalweg"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.memberCount").value(5))
                .andExpect(jsonPath("$.myRank").value(5))
                .andExpect(jsonPath("$.myPoints").value(0));
        assertEquals(7L, groupManager.lastUserId);
        assertEquals("k7Qp2xZ9aB", groupManager.lastJoinCode);
    }

    @Test
    void joinTrimsTheInviteCode() throws Exception {
        join("{\"inviteCode\":\"  k7Qp2xZ9aB \"}").andExpect(status().isOk());

        assertEquals("k7Qp2xZ9aB", groupManager.lastJoinCode);
    }

    @Test
    void joinUsesTheUserIdFromTheTokenNotFromTheBody() throws Exception {
        join("{\"inviteCode\":\"k7Qp2xZ9aB\",\"userId\":99}").andExpect(status().isOk());

        assertEquals(7L, groupManager.lastUserId);
    }

    @Test
    void joinWithBlankOrMissingCodeReturnsValidationError() throws Exception {
        join("{\"inviteCode\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Bitte einen Einladungscode angeben."));
        join("{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertEquals(0, groupManager.joinCalls);
    }

    @Test
    void joinWithInvalidCodeReturns404() throws Exception {
        groupManager.joinFailWith = new ApiException(HttpStatus.NOT_FOUND, "INVITE_INVALID", "Dieser Einladungscode ist ungültig.");

        join("{\"inviteCode\":\"falsch\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INVITE_INVALID"));
    }

    @Test
    void joinWhenAlreadyMemberReturns409() throws Exception {
        groupManager.joinFailWith = new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "Du bist schon Mitglied dieser Tippgruppe.");

        join("{\"inviteCode\":\"k7Qp2xZ9aB\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_MEMBER"));
    }

    @Test
    void joinWhenGroupIsFullReturns409() throws Exception {
        groupManager.joinFailWith = new ApiException(HttpStatus.CONFLICT, "GROUP_FULL", "Diese Tippgruppe ist voll.");

        join("{\"inviteCode\":\"k7Qp2xZ9aB\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GROUP_FULL"));
    }

    @Test
    void joinWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/groups/join").contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteCode\":\"k7Qp2xZ9aB\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        assertEquals(0, groupManager.joinCalls);
    }

    // Merkt sich die letzten Aufrufe
    private static class FakeGroupManager implements GroupManager {

        long lastUserId;
        CreateGroupRequest lastRequest;
        int createCalls;
        int detailsCalls;
        int joinCalls;
        String lastJoinCode;
        ApiException joinFailWith;
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

        @Override
        public InvitePreview getInvitePreview(String inviteCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GroupSummary joinGroup(long userId, String inviteCode) {
            joinCalls++;
            lastUserId = userId;
            lastJoinCode = inviteCode;
            if (joinFailWith != null) {
                throw joinFailWith;
            }
            return new GroupSummary(3, "WG Lohrtalweg", "MEMBER", 5, 5, 0);
        }

        @Override
        public GroupDetails getGroupDetails(long groupId, boolean includeInviteCode) {
            detailsCalls++;
            return new GroupDetails(groupId, "WG Lohrtalweg", includeInviteCode ? "k7Qp2xZ9aB" : null,
                    List.of(new Member(7, "niklas", "ADMIN", JOINED), new Member(12, "maximilian", "MEMBER", JOINED)));
        }
    }

    // Ohne Datenbank: liefert die eingestellte Rolle oder wirft den eingestellten Fehler
    private static class FakeGroupAccess extends GroupAccess {

        String role = "MEMBER";
        ApiException failWith;
        long lastGroupId;
        long lastUserId;

        FakeGroupAccess() {
            super(null);
        }

        @Override
        public Membership requireMember(long groupId, long userId) {
            lastGroupId = groupId;
            lastUserId = userId;
            if (failWith != null) {
                throw failWith;
            }
            return new Membership(groupId, userId, role, JOINED);
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
