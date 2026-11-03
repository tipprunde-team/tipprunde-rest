package com.mosbach.tipprunde.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.mosbach.tipprunde.data.api.AuthManager;
import com.mosbach.tipprunde.data.api.RankingManager;
import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.error.GlobalExceptionHandler;
import com.mosbach.tipprunde.model.auth.TokenAnswer;
import com.mosbach.tipprunde.model.ranking.RankingEntry;
import com.mosbach.tipprunde.security.AuthInterceptor;
import com.mosbach.tipprunde.security.CurrentUserIdArgumentResolver;
import com.mosbach.tipprunde.security.GroupAccess;
import com.mosbach.tipprunde.security.Membership;

// KI-Log MA-20 "Rangliste im Format des API-Designs mit Tests, passend zu web #52."
// Standalone-Test mit Platzhaltern, daher keine Datenbank nötig
class RankingControllerTest {

    private static final String TOKEN = "Bearer gueltiges-token";

    private final FakeRankingManager rankingManager = new FakeRankingManager();
    private final FakeGroupAccess groupAccess = new FakeGroupAccess();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RankingController(groupAccess, rankingManager))
            .addInterceptors(new AuthInterceptor(new FakeAuthManager()))
            .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void totalRankingHasGroupIdNullMatchdayAndEntriesInOrder() throws Exception {
        mockMvc.perform(get("/api/groups/3/ranking").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(3))
                .andExpect(jsonPath("$.matchday").value((Object) null))
                .andExpect(jsonPath("$.ranking.length()").value(4))
                .andExpect(jsonPath("$.ranking[0].rank").value(1))
                .andExpect(jsonPath("$.ranking[0].userId").value(12))
                .andExpect(jsonPath("$.ranking[0].username").value("maximilian"))
                .andExpect(jsonPath("$.ranking[0].points").value(35))
                .andExpect(jsonPath("$.ranking[0].exactTips").value(4))
                .andExpect(jsonPath("$.ranking[1].rank").value(2))
                .andExpect(jsonPath("$.ranking[2].rank").value(2))
                .andExpect(jsonPath("$.ranking[3].rank").value(4));
        assertEquals(3L, rankingManager.lastGroupId);
        assertEquals(null, rankingManager.lastMatchday);
    }

    @Test
    void matchdayParameterIsPassedOnAndReturned() throws Exception {
        mockMvc.perform(get("/api/groups/3/ranking?matchday=6").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchday").value(6));
        assertEquals(6, rankingManager.lastMatchday);
    }

    @Test
    void firstAndLastMatchdayAreAccepted() throws Exception {
        mockMvc.perform(get("/api/groups/3/ranking?matchday=1").header("Authorization", TOKEN)).andExpect(status().isOk());
        mockMvc.perform(get("/api/groups/3/ranking?matchday=34").header("Authorization", TOKEN)).andExpect(status().isOk());
    }

    @Test
    void matchdayOutOfRangeReturnsValidationError() throws Exception {
        mockMvc.perform(get("/api/groups/3/ranking?matchday=0").header("Authorization", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Der Spieltag muss zwischen 1 und 34 liegen."));
        mockMvc.perform(get("/api/groups/3/ranking?matchday=35").header("Authorization", TOKEN))
                .andExpect(status().isBadRequest());
        assertEquals(0, rankingManager.calls);
    }

    @Test
    void matchdayWithoutNumberReturnsValidationError() throws Exception {
        mockMvc.perform(get("/api/groups/3/ranking?matchday=abc").header("Authorization", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void emptyRankingIsAnEmptyArray() throws Exception {
        rankingManager.entries = List.of();

        mockMvc.perform(get("/api/groups/3/ranking").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranking.length()").value(0));
    }

    @Test
    void foreignGroupReturns403AndNeverReadsTheRanking() throws Exception {
        groupAccess.failWith = new ApiException(HttpStatus.FORBIDDEN, "NOT_A_MEMBER", "Du bist kein Mitglied dieser Tippgruppe.");

        mockMvc.perform(get("/api/groups/3/ranking").header("Authorization", TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_A_MEMBER"));
        assertEquals(0, rankingManager.calls);
    }

    @Test
    void membershipIsCheckedBeforeTheMatchdayInput() throws Exception {
        groupAccess.failWith = new ApiException(HttpStatus.FORBIDDEN, "NOT_A_MEMBER", "Du bist kein Mitglied dieser Tippgruppe.");

        mockMvc.perform(get("/api/groups/3/ranking?matchday=99").header("Authorization", TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownGroupReturns404() throws Exception {
        groupAccess.failWith = new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Diese Tippgruppe gibt es nicht.");

        mockMvc.perform(get("/api/groups/999/ranking").header("Authorization", TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void withoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/groups/3/ranking"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        assertEquals(0, rankingManager.calls);
    }

    @Test
    void userIdForTheMembershipCheckComesFromTheToken() throws Exception {
        mockMvc.perform(get("/api/groups/3/ranking").header("Authorization", TOKEN)).andExpect(status().isOk());

        assertEquals(7L, groupAccess.lastUserId);
        assertEquals(3L, groupAccess.lastGroupId);
    }

    private static class FakeRankingManager implements RankingManager {

        long lastGroupId;
        Integer lastMatchday;
        int calls;
        List<RankingEntry> entries = List.of(
                new RankingEntry(1, 12, "maximilian", 35, 4), new RankingEntry(2, 7, "niklas", 31, 3),
                new RankingEntry(2, 21, "max", 31, 2), new RankingEntry(4, 30, "arlind", 27, 2));

        @Override
        public List<RankingEntry> getRanking(long groupId, Integer matchday) {
            calls++;
            lastGroupId = groupId;
            lastMatchday = matchday;
            return entries;
        }
    }

    // Ohne Datenbank: lässt jeden durch oder wirft den eingestellten Fehler
    private static class FakeGroupAccess extends GroupAccess {

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
            return new Membership(groupId, userId, "MEMBER", Instant.parse("2026-10-19T18:00:00Z"));
        }
    }

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
