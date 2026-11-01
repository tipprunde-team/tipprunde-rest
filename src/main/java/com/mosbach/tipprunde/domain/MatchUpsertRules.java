package com.mosbach.tipprunde.domain;

import java.util.Objects;

import com.mosbach.tipprunde.model.match.Match;
import com.mosbach.tipprunde.model.match.MatchStatus;
import com.mosbach.tipprunde.model.match.MatchUpdate;
import com.mosbach.tipprunde.model.match.UpsertResult.Outcome;

// Reine Regeln für die idempotente Übernahme von Spielen aus der Queue (Idempotent Receiver)
public final class MatchUpsertRules {

    private MatchUpsertRules() {
    }

    // Neues Spiel anlegen, manuell korrigierte Spiele nie überschreiben, gleichen Stand ignorieren
    public static Outcome decide(Match existing, MatchUpdate update) {
        if (existing == null) {
            return Outcome.INSERTED;
        }
        if (existing.manuallyCorrected()) {
            return Outcome.MANUALLY_CORRECTED;
        }
        return sameState(existing, update) ? Outcome.UNCHANGED : Outcome.UPDATED;
    }

    // Punkte neu berechnen, wenn ein Spiel beendet ist und sich Status oder Ergebnis geändert haben
    public static boolean resultChanged(Match existing, MatchUpdate update) {
        if (update.status() != MatchStatus.FINISHED || update.homeGoals() == null || update.awayGoals() == null) {
            return false;
        }
        return existing == null
                || existing.status() != MatchStatus.FINISHED
                || !Objects.equals(existing.homeGoals(), update.homeGoals())
                || !Objects.equals(existing.awayGoals(), update.awayGoals());
    }

    static boolean sameState(Match existing, MatchUpdate update) {
        return existing.matchday() == update.matchday()
                && Objects.equals(existing.kickoffUtc(), update.kickoffUtc())
                && existing.status() == update.status()
                && Objects.equals(existing.homeTeam(), update.homeTeam())
                && Objects.equals(existing.homeTeamShort(), update.homeTeamShort())
                && Objects.equals(existing.homeCrest(), update.homeCrest())
                && Objects.equals(existing.awayTeam(), update.awayTeam())
                && Objects.equals(existing.awayTeamShort(), update.awayTeamShort())
                && Objects.equals(existing.awayCrest(), update.awayCrest())
                && Objects.equals(existing.homeGoals(), update.homeGoals())
                && Objects.equals(existing.awayGoals(), update.awayGoals());
    }
}
