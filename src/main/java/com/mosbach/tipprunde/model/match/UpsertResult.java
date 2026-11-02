package com.mosbach.tipprunde.model.match;

// Ergebnis der Übernahme eines Spiels; tipsScored zählt die Tipps, deren Punkte gesetzt oder gelöscht wurden
public record UpsertResult(Outcome outcome, Long matchId, boolean resultChanged, int tipsScored) {

    public enum Outcome {
        INSERTED,
        UPDATED,
        UNCHANGED,
        MANUALLY_CORRECTED
    }
}
