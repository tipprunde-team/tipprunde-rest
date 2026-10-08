package com.mosbach.tipprunde.model.match;

import java.time.Instant;

// Spiel, entspricht Tabelle matches; Zeiten in UTC
public record Match(
        Long id,
        Long externalId,
        int matchday,
        Instant kickoffUtc,
        MatchStatus status,
        String homeTeam,
        String homeTeamShort,
        String homeCrest,
        String awayTeam,
        String awayTeamShort,
        String awayCrest,
        Integer homeGoals,          // null, solange kein Ergebnis
        Integer awayGoals,          // null, solange kein Ergebnis
        boolean manuallyCorrected,
        Instant updatedAt
) {

    // Ergebnis vorhanden?
    public boolean hasResult() {
        return homeGoals != null && awayGoals != null;
    }
}