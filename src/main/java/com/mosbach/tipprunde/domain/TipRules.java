package com.mosbach.tipprunde.domain;

import java.time.Instant;

import org.springframework.http.HttpStatus;

import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.model.match.Match;
import com.mosbach.tipprunde.model.match.MatchStatus;
import com.mosbach.tipprunde.security.Membership;

// Pure tip rules without database; server time is passed in (UC-01, US-20, US-21)
public final class TipRules {

    public static final int MIN_GOALS = 0;
    public static final int MAX_GOALS = 20;

    private TipRules() {
    }

    // Locked from kickoff on, or as soon as the match is no longer upcoming
    public static boolean isLocked(Match match, Instant now) {
        return match.status() != MatchStatus.UPCOMING || !now.isBefore(match.kickoffUtc());
    }

    // Only whole JSON numbers 0..20; fractions (1.5), strings ("2") and missing values are rejected
    public static int parseGoals(Object value) {
        if (value instanceof Integer goals && goals >= MIN_GOALS && goals <= MAX_GOALS) {
            return goals;
        }
        throw invalidScore();
    }

    // Order as in the API reference: score > lock > kickoff after joining
    public static void check(Match match, Membership membership, int home, int away, Instant now) {
        if (home < MIN_GOALS || home > MAX_GOALS || away < MIN_GOALS || away > MAX_GOALS) {
            throw invalidScore();
        }
        if (isLocked(match, now)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TIP_LOCKED",
                    "Das Spiel hat schon begonnen, der Tipp ist gesperrt.");
        }
        // Safety net for FA-39: only reachable if joinedAt lies after now, otherwise TIP_LOCKED fires first
        if (match.kickoffUtc().isBefore(membership.joinedAt())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MATCH_BEFORE_JOIN",
                    "Dieses Spiel hat vor deinem Beitritt zur Tippgruppe begonnen.");
        }
    }

    private static ApiException invalidScore() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCORE",
                "Bitte für beide Teams eine ganze Zahl von 0 bis 20 angeben.");
    }
}
