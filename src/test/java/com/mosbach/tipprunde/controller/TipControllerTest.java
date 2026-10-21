package com.mosbach.tipprunde.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.mosbach.tipprunde.data.api.AuthManager;
import com.mosbach.tipprunde.data.api.MatchManager;
import com.mosbach.tipprunde.data.api.TipManager;
import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.error.GlobalExceptionHandler;
import com.mosbach.tipprunde.model.auth.TokenAnswer;
import com.mosbach.tipprunde.model.match.Match;
import com.mosbach.tipprunde.model.match.MatchStatus;
import com.mosbach.tipprunde.model.tip.Tip;
import com.mosbach.tipprunde.security.AuthInterceptor;
import com.mosbach.tipprunde.security.CurrentUserIdArgumentResolver;
import com.mosbach.tipprunde.security.GroupAccess;
import com.mosbach.tipprunde.security.Membership;

// Web slice test for PUT .../tip with fakes and a fixed clock, no database needed
class TipControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final long OPEN_MATCH = 20L;
    private static final long STARTED_MATCH = 10L;

    private final FakeTipManager tipManager = new FakeTipManager();
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TipController(new FakeGroupAccess(), new FakeMatchManager(), tipManager,
                    Clock.fixed(NOW, ZoneOffset.UTC)))
            .addInterceptors(new AuthInterceptor(new FakeAuthManager()))
            .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private ResultActions putTip(long groupId, long matchId, String body) throws Exception {
        return mockMvc.perform(put("/api/groups/" + groupId + "/matches/" + matchId + "/tip")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void tipBeforeKickoffIsSaved() throws Exception {
        putTip(3, OPEN_MATCH, "{\"home\":2,\"away\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(20))
                .andExpect(jsonPath("$.home").value(2))
                .andExpect(jsonPath("$.away").value(1))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-21T12:00:00Z"));
    }

    @Test
    void secondTipReplacesTheFirst() throws Exception {
        putTip(3, OPEN_MATCH, "{\"home\":2,\"away\":1}").andExpect(status().isOk());
        putTip(3, OPEN_MATCH, "{\"home\":0,\"away\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.home").value(0));
        assertEquals(1, tipManager.tips.size());
    }

    @Test
    void invalidScoresAreRejectedAndNothingIsSaved() throws Exception {
        for (String body : List.of("{\"home\":-1,\"away\":1}", "{\"home\":21,\"away\":1}",
                "{\"home\":1.5,\"away\":1}", "{\"home\":\"2\",\"away\":1}", "{\"away\":1}", "{}")) {
            putTip(3, OPEN_MATCH, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_SCORE"));
        }
        assertTrue(tipManager.tips.isEmpty());
    }

    @Test
    void startedMatchReturnsTipLocked() throws Exception {
        putTip(3, STARTED_MATCH, "{\"home\":2,\"away\":1}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TIP_LOCKED"));
        assertTrue(tipManager.tips.isEmpty());
    }

    @Test
    void foreignGroupReturnsNotAMember() throws Exception {
        putTip(4, OPEN_MATCH, "{\"home\":2,\"away\":1}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_MEMBER"));
    }

    @Test
    void unknownGroupReturns404() throws Exception {
        putTip(999, OPEN_MATCH, "{\"home\":2,\"away\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void unknownMatchReturnsMatchNotFound() throws Exception {
        putTip(3, 999, "{\"home\":2,\"away\":1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MATCH_NOT_FOUND"));
    }

    @Test
    void withoutTokenReturns401() throws Exception {
        mockMvc.perform(put("/api/groups/3/matches/20/tip")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"home\":2,\"away\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // User 7 is member of group 3 since yesterday; group 4 exists, everything else does not
    private static class FakeGroupAccess extends GroupAccess {

        FakeGroupAccess() {
            super(null);
        }

        @Override
        public Membership requireMember(long groupId, long userId) {
            if (groupId != 3L && groupId != 4L) {
                throw new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Diese Tippgruppe gibt es nicht.");
            }
            if (groupId != 3L || userId != 7L) {
                throw new ApiException(HttpStatus.FORBIDDEN, "NOT_A_MEMBER", "Du bist kein Mitglied dieser Tippgruppe.");
            }
            return new Membership(groupId, userId, Membership.MEMBER, NOW.minusSeconds(86400));
        }
    }

    // Match 20 starts in two days, match 10 started an hour ago
    private static class FakeMatchManager implements MatchManager {

        @Override
        public List<Match> getMatchesByMatchday(int matchday) {
            return List.of();
        }

        @Override
        public Optional<Match> getMatchById(long id) {
            if (id == OPEN_MATCH) {
                return Optional.of(match(OPEN_MATCH, NOW.plusSeconds(2 * 86400)));
            }
            if (id == STARTED_MATCH) {
                return Optional.of(match(STARTED_MATCH, NOW.minusSeconds(3600)));
            }
            return Optional.empty();
        }

        @Override
        public int getCurrentMatchday() {
            return 7;
        }

        private static Match match(long id, Instant kickoff) {
            return new Match(id, 565800L + id, 7, kickoff, MatchStatus.UPCOMING, "VfB Stuttgart", "Stuttgart", null,
                    "Borussia Mönchengladbach", "M'gladbach", null, null, null, false, NOW);
        }
    }

    // Keeps tips in a map, the key mirrors UNIQUE (group_id, user_id, match_id)
    private static class FakeTipManager implements TipManager {

        final Map<String, Tip> tips = new HashMap<>();

        @Override
        public List<Tip> getOwnTipsForMatchday(long groupId, long userId, int matchday) {
            return List.copyOf(tips.values());
        }

        @Override
        public Tip saveTip(long groupId, long userId, long matchId, int home, int away, Instant now) {
            Tip tip = new Tip(1L, groupId, userId, matchId, home, away, null, now);
            tips.put(groupId + "/" + userId + "/" + matchId, tip);
            return tip;
        }
    }

    // Only "valid-token" is accepted, it belongs to user 7
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
            return "valid-token".equals(token) ? Optional.of(7L) : Optional.empty();
        }
    }
}
