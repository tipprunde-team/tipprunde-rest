package com.mosbach.tipprunde.model.match;

import java.time.Instant;

import com.mosbach.tipprunde.model.tip.Tip;

// One match in the matchday response, including the caller's own tip
public record MatchEntry(
        long id,
        Instant kickoff,
        MatchStatus status,
        boolean locked,
        Team homeTeam,
        Team awayTeam,
        Score result,       // null until finished
        Score myTip,        // null if no tip yet
        Integer myPoints) { // null until scored

    public record Team(String name, String shortName, String crest) {
    }

    public record Score(int home, int away) {
    }

    public static MatchEntry of(Match match, Tip ownTip, boolean locked) {
        return new MatchEntry(
                match.id(),
                match.kickoffUtc(),
                match.status(),
                locked,
                new Team(match.homeTeam(), match.homeTeamShort(), match.homeCrest()),
                new Team(match.awayTeam(), match.awayTeamShort(), match.awayCrest()),
                match.hasResult() ? new Score(match.homeGoals(), match.awayGoals()) : null,
                ownTip != null ? new Score(ownTip.homeGoals(), ownTip.awayGoals()) : null,
                ownTip != null ? ownTip.points() : null);
    }
}
