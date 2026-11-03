package com.mosbach.tipprunde.model.match;

import java.time.Instant;

// KI-Log AH-15 "Übernimm Spiele aus der Queue so, dass ein doppeltes Event nichts doppelt schreibt."
// Neuer Stand eines Spiels aus dem Match-Service, Felder wie Tabelle matches (ohne id)
public record MatchUpdate(
        long externalId,
        int matchday,
        Instant kickoffUtc,
        MatchStatus status,
        String homeTeam,
        String homeTeamShort,
        String homeCrest,
        String awayTeam,
        String awayTeamShort,
        String awayCrest,
        Integer homeGoals,
        Integer awayGoals) {
}
