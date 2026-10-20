package com.mosbach.tipprunde.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.mosbach.tipprunde.model.match.Match;
import com.mosbach.tipprunde.model.match.MatchStatus;

// Boundary tests for the tip lock with a fixed time instead of the real clock
class TipRulesTest {

    private static final Instant KICKOFF = Instant.parse("2026-10-23T18:30:00Z");

    private static Match match(MatchStatus status) {
        return new Match(1L, 565833L, 7, KICKOFF, status, "VfB Stuttgart", "Stuttgart", null,
                "Borussia Mönchengladbach", "M'gladbach", null, null, null, false, KICKOFF);
    }

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
}
