package com.mosbach.tipprunde.domain;

import java.time.Instant;

import com.mosbach.tipprunde.model.match.Match;
import com.mosbach.tipprunde.model.match.MatchStatus;

// Pure tip rules without database; server time is passed in (UC-01, US-21)
public final class TipRules {

    private TipRules() {
    }

    // Locked from kickoff on, or as soon as the match is no longer upcoming
    public static boolean isLocked(Match match, Instant now) {
        return match.status() != MatchStatus.UPCOMING || !now.isBefore(match.kickoffUtc());
    }
}
