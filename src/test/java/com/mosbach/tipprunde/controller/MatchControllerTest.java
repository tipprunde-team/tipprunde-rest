package com.mosbach.tipprunde.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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

// Standalone test with fakes and a fixed clock, no database needed
class MatchControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final long MY_GROUP = 3L;
    private static final long OTHER_GROUP = 4L;

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new MatchController(new FakeGroupAccess(), new FakeMatchManager(),
                    new FakeTipManager(), Clock.fixed(NOW, ZoneOffset.UTC)))
            .addInterceptors(new AuthInterceptor(new FakeAuthManager()))
            .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private ResultActions getAsMember(String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer valid-token"));
    }

    @Test
    void withoutMatchdayReturnsCurrentMatchdayWithServerTime() throws Exception {
        getAsMember("/api/groups/3/matches")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchday").value(7))
                .andExpect(jsonPath("$.serverTime").value("2026-10-21T12:00:00Z"))
                .andExpect(jsonPath("$.matches.length()").value(1))
                .andExpect(jsonPath("$.matches[0].kickoff").value("2026-10-23T18:30:00Z"))
                .andExpect(jsonPath("$.matches[0].status").value("UPCOMING"))
                .andExpect(jsonPath("$.matches[0].locked").value(false))
                .andExpect(jsonPath("$.matches[0].homeTeam.shortName").value("Stuttgart"))
                .andExpect(jsonPath("$.matches[0].result").isEmpty())
                .andExpect(jsonPath("$.matches[0].myTip.home").value(2))
                .andExpect(jsonPath("$.matches[0].myTip.away").value(1))
                .andExpect(jsonPath("$.matches[0].myPoints").isEmpty());
    }

    @Test
    void finishedMatchIsLockedAndHasResult() throws Exception {
        getAsMember("/api/groups/3/matches?matchday=6")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchday").value(6))
                .andExpect(jsonPath("$.matches[0].locked").value(true))
                .andExpect(jsonPath("$.matches[0].result.home").value(6))
                .andExpect(jsonPath("$.matches[0].result.away").value(1))
                .andExpect(jsonPath("$.matches[0].myTip").isEmpty());
    }

    @Test
    void matchdayWithoutMatchesReturnsEmptyList() throws Exception {
        getAsMember("/api/groups/3/matches?matchday=20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchday").value(20))
                .andExpect(jsonPath("$.matches.length()").value(0));
    }

    @Test
    void matchdayOutsideOneTo34IsRejected() throws Exception {
        getAsMember("/api/groups/3/matches?matchday=0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        getAsMember("/api/groups/3/matches?matchday=35")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void matchdayThatIsNoNumberIsRejected() throws Exception {
        getAsMember("/api/groups/3/matches?matchday=abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nonMemberGets403() throws Exception {
        getAsMember("/api/groups/4/matches")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_MEMBER"));
    }

    @Test
    void unknownGroupGets404() throws Exception {
        getAsMember("/api/groups/999/matches")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void withoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/groups/3/matches"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // User 7 is member of group 3 only; group 4 exists, everything else does not
    private static class FakeGroupAccess extends GroupAccess {

        FakeGroupAccess() {
            super(null);
        }

        @Override
        public Membership requireMember(long groupId, long userId) {
            if (groupId != MY_GROUP && groupId != OTHER_GROUP) {
                throw new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Diese Tippgruppe gibt es nicht.");
            }
            if (groupId != MY_GROUP || userId != 7L) {
                throw new ApiException(HttpStatus.FORBIDDEN, "NOT_A_MEMBER", "Du bist kein Mitglied dieser Tippgruppe.");
            }
            return new Membership(groupId, userId, Membership.MEMBER, NOW.minusSeconds(86400));
        }
    }

    // Matchday 6 finished, matchday 7 upcoming, current matchday is 7
    private static class FakeMatchManager implements MatchManager {

        private static final Match FINISHED = new Match(10L, 565825L, 6, Instant.parse("2026-10-17T16:30:00Z"),
                MatchStatus.FINISHED, "FC Bayern München", "Bayern", null, "RB Leipzig", "RB Leipzig", null,
                6, 1, false, NOW);
        private static final Match UPCOMING = new Match(20L, 565833L, 7, Instant.parse("2026-10-23T18:30:00Z"),
                MatchStatus.UPCOMING, "VfB Stuttgart", "Stuttgart", null, "Borussia Mönchengladbach", "M'gladbach",
                null, null, null, false, NOW);

        @Override
        public List<Match> getMatchesByMatchday(int matchday) {
            return switch (matchday) {
                case 6 -> List.of(FINISHED);
                case 7 -> List.of(UPCOMING);
                default -> List.of();
            };
        }

        @Override
        public Optional<Match> getMatchById(long id) {
            return Optional.empty();
        }

        @Override
        public int getCurrentMatchday() {
            return 7;
        }
    }

    // Own tip 2:1 on the upcoming match
    private static class FakeTipManager implements TipManager {

        @Override
        public List<Tip> getOwnTipsForMatchday(long groupId, long userId, int matchday) {
            return matchday == 7 ? List.of(new Tip(1L, groupId, userId, 20L, 2, 1, null, NOW)) : List.of();
        }

        @Override
        public Tip saveTip(long groupId, long userId, long matchId, int home, int away, Instant now) {
            throw new UnsupportedOperationException();
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
