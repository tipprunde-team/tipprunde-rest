package com.mosbach.tipprunde.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.model.match.Match;
import com.mosbach.tipprunde.model.match.MatchStatus;
import com.mosbach.tipprunde.security.Membership;

// Boundary tests for tip rules with a fixed time instead of the real clock
class TipRulesTest {

    private static final Instant KICKOFF = Instant.parse("2026-10-23T18:30:00Z");
    private static final Membership MEMBER =
            new Membership(3, 7, Membership.MEMBER, KICKOFF.minus(Duration.ofDays(3)));

    private static Match match(MatchStatus status) {
        return new Match(1L, 565833L, 7, KICKOFF, status, "VfB Stuttgart", "Stuttgart", null,
                "Borussia Mönchengladbach", "M'gladbach", null, null, null, false, KICKOFF);
    }

    private static String codeOf(Runnable action) {
        return assertThrows(ApiException.class, action::run).getCode();
    }

    // Lock state

    @Test
    void oneMinuteBeforeKickoffIsOpen() {
        assertFalse(TipRules.isLocked(match(MatchStatus.UPCOMING), KICKOFF.minus(Duration.ofMinutes(1))));
    }

    @Test
    void exactlyAtKickoffIsLocked() {
        assertTrue(TipRules.isLocked(match(MatchStatus.UPCOMING), KICKOFF));
    }

    @Test
    void oneMinuteAfterKickoffIsLocked() {
        assertTrue(TipRules.isLocked(match(MatchStatus.UPCOMING), KICKOFF.plus(Duration.ofMinutes(1))));
    }

    @Test
    void liveFinishedPostponedAndCancelledAreLockedEvenBeforeKickoff() {
        Instant before = KICKOFF.minus(Duration.ofDays(1));
        assertTrue(TipRules.isLocked(match(MatchStatus.LIVE), before));
        assertTrue(TipRules.isLocked(match(MatchStatus.FINISHED), before));
        assertTrue(TipRules.isLocked(match(MatchStatus.POSTPONED), before));
        assertTrue(TipRules.isLocked(match(MatchStatus.CANCELLED), before));
    }

    // Goals: whole numbers 0 to 20

    @Test
    void goalsZeroAndTwentyAreAccepted() {
        assertEquals(0, TipRules.parseGoals(0));
        assertEquals(20, TipRules.parseGoals(20));
    }

    @Test
    void goalsMinusOneAndTwentyOneAreRejected() {
        assertEquals("INVALID_SCORE", codeOf(() -> TipRules.parseGoals(-1)));
        assertEquals("INVALID_SCORE", codeOf(() -> TipRules.parseGoals(21)));
    }

    @Test
    void missingFractionAndTextAreRejected() {
        assertEquals("INVALID_SCORE", codeOf(() -> TipRules.parseGoals(null)));
        assertEquals("INVALID_SCORE", codeOf(() -> TipRules.parseGoals(1.5)));
        assertEquals("INVALID_SCORE", codeOf(() -> TipRules.parseGoals("2")));
        assertEquals("INVALID_SCORE", codeOf(() -> TipRules.parseGoals(3_000_000_000L)));
    }

    // Complete check in the order of the API reference

    @Test
    void validTipOneMinuteBeforeKickoffPasses() {
        assertDoesNotThrow(() -> TipRules.check(match(MatchStatus.UPCOMING), MEMBER, 2, 1,
                KICKOFF.minus(Duration.ofMinutes(1))));
    }

    @Test
    void tipAtOrAfterKickoffIsLocked() {
        assertEquals("TIP_LOCKED", codeOf(() -> TipRules.check(match(MatchStatus.UPCOMING), MEMBER, 2, 1, KICKOFF)));
        assertEquals("TIP_LOCKED", codeOf(() -> TipRules.check(match(MatchStatus.UPCOMING), MEMBER, 2, 1,
                KICKOFF.plus(Duration.ofMinutes(1)))));
    }

    @Test
    void cancelledMatchIsLocked() {
        assertEquals("TIP_LOCKED", codeOf(() -> TipRules.check(match(MatchStatus.CANCELLED), MEMBER, 2, 1,
                KICKOFF.minus(Duration.ofDays(1)))));
    }

    @Test
    void invalidScoreIsCheckedBeforeLock() {
        assertEquals("INVALID_SCORE", codeOf(() -> TipRules.check(match(MatchStatus.FINISHED), MEMBER, 21, 1,
                KICKOFF.plus(Duration.ofDays(1)))));
    }

    @Test
    void matchBeforeJoiningIsLockedInPractice() {
        // Joined after kickoff: at that time the match has already started, so TIP_LOCKED comes first (FA-39)
        Membership lateMember = new Membership(3, 7, Membership.MEMBER, KICKOFF.plus(Duration.ofHours(1)));
        assertEquals("TIP_LOCKED", codeOf(() -> TipRules.check(match(MatchStatus.UPCOMING), lateMember, 2, 1,
                KICKOFF.plus(Duration.ofHours(2)))));
    }

    @Test
    void matchBeforeJoinIsStillCaughtIfLockDoesNotApply() {
        // Theoretical case (joinedAt in the future), keeps the safety net tested
        Membership futureMember = new Membership(3, 7, Membership.MEMBER, KICKOFF.plus(Duration.ofHours(1)));
        assertEquals("MATCH_BEFORE_JOIN", codeOf(() -> TipRules.check(match(MatchStatus.UPCOMING), futureMember, 2, 1,
                KICKOFF.minus(Duration.ofMinutes(1)))));
    }
}
