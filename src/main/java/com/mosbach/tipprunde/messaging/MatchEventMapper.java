package com.mosbach.tipprunde.messaging;

import com.mosbach.tipprunde.model.match.MatchStatus;
import com.mosbach.tipprunde.model.match.MatchUpdate;

// Prüft ein Event und bildet es auf MatchUpdate ab; ungültige Events werden abgelehnt statt halb gespeichert
public final class MatchEventMapper {

    private MatchEventMapper() {
    }

    public static MatchUpdate toUpdate(MatchEvent event) {
        if (event == null || !MatchEvent.TYPE.equals(event.type())) {
            throw new IllegalArgumentException("Unsupported event type");
        }
        if (event.externalId() == null || event.externalId() <= 0 || event.matchday() == null
                || event.matchday() < 1 || event.matchday() > 34 || event.kickoffUtc() == null
                || event.homeTeam() == null || event.awayTeam() == null
                || isBlank(event.homeTeam().name()) || isBlank(event.awayTeam().name())) {
            throw new IllegalArgumentException("Incomplete event for externalId " + event.externalId());
        }
        MatchStatus status = MatchStatus.valueOf(event.status());
        // Ergebnis nur bei FINISHED übernehmen und nur mit Toren von 0 bis 20 (wie bei den Tipps)
        Integer home = null;
        Integer away = null;
        if (status == MatchStatus.FINISHED && event.score() != null && validGoals(event.score().home())
                && validGoals(event.score().away())) {
            home = event.score().home();
            away = event.score().away();
        }
        return new MatchUpdate(event.externalId(), event.matchday(), event.kickoffUtc(), status,
                event.homeTeam().name(), event.homeTeam().shortName(), event.homeTeam().crest(),
                event.awayTeam().name(), event.awayTeam().shortName(), event.awayTeam().crest(), home, away);
    }

    private static boolean validGoals(Integer goals) {
        return goals != null && goals >= 0 && goals <= 20;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
