package com.mosbach.tipprunde.model.match;

// Ergebnis der Übernahme eines Spiels; resultChanged ist der Auslöser für die Punkteberechnung
public record UpsertResult(Outcome outcome, Long matchId, boolean resultChanged) {

    public enum Outcome {
        INSERTED,
        UPDATED,
        UNCHANGED,
        MANUALLY_CORRECTED
    }
}
