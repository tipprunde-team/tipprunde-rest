package com.mosbach.tipprunde.model.match;

// KI-Log AH-15 "Übernimm Spiele aus der Queue so, dass ein doppeltes Event nichts doppelt schreibt."
// KI-Log AH-16 "Berechne die Punkte der Tipps, sobald ein Spiel als beendet gemeldet wird."
// Ergebnis der Übernahme eines Spiels; tipsScored zählt die Tipps, deren Punkte gesetzt oder gelöscht wurden
public record UpsertResult(Outcome outcome, Long matchId, boolean resultChanged, int tipsScored) {

    public enum Outcome {
        INSERTED,
        UPDATED,
        UNCHANGED,
        MANUALLY_CORRECTED
    }
}
