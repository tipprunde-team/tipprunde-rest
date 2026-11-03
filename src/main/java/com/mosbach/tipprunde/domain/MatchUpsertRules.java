package com.mosbach.tipprunde.domain;

import java.util.Objects;

import com.mosbach.tipprunde.model.match.Match;
import com.mosbach.tipprunde.model.match.MatchStatus;
import com.mosbach.tipprunde.model.match.MatchUpdate;
import com.mosbach.tipprunde.model.match.UpsertResult.Outcome;

// KI-Log AH-15, AH-16
// Reine Regeln für die idempotente Übernahme von Spielen aus der Queue (Idempotent Receiver)
public final class MatchUpsertRules {

    // Was mit den Punkten der Tipps zu einem Spiel passiert
    public enum Scoring {
        ALL,        // Ergebnis neu oder geändert: alle Tipps neu werten (FA-45)
        MISSING,    // Ergebnis steht schon: nur Tipps ohne Punkte werten (z. B. alte Spiele aus dem Seed)
        CLEAR,      // Spiel nicht mehr beendet, z. B. abgesagt: Punkte entfernen (FA-28)
        NONE
    }

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

    // Gewertet wird immer das Ergebnis, das danach in der Tabelle steht: bei manuell korrigierten
    // oder unveränderten Spielen das gespeicherte, sonst das neue aus dem Event
    public static Scoring scoring(Match existing, MatchUpdate update, Outcome outcome) {
        switch (outcome) {
            case INSERTED:
                return Scoring.NONE; // neues Spiel, dazu gibt es noch keine Tipps
            case UPDATED:
                if (isScorable(update.status(), update.homeGoals(), update.awayGoals())) {
                    return resultChanged(existing, update) ? Scoring.ALL : Scoring.MISSING;
                }
                return existing.status() == MatchStatus.FINISHED || existing.hasResult() ? Scoring.CLEAR : Scoring.NONE;
            default:
                return isScorable(existing.status(), existing.homeGoals(), existing.awayGoals())
                        ? Scoring.MISSING : Scoring.NONE;
        }
    }

    private static boolean isScorable(MatchStatus status, Integer home, Integer away) {
        return status == MatchStatus.FINISHED && home != null && away != null;
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
