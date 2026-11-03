package com.mosbach.tipprunde.domain;

// KI-Log AH-16 "Berechne die Punkte der Tipps, sobald ein Spiel als beendet gemeldet wird."
// Punkteschema FA-41 bis FA-44 als reine Funktion, gewertet wird das Ergebnis nach 90 Minuten (FA-40)
public final class PointsCalculator {

    public static final int EXACT = 4;
    public static final int GOAL_DIFFERENCE = 3;
    public static final int TENDENCY = 2;
    public static final int WRONG = 0;

    private PointsCalculator() {
    }

    // 4 exaktes Ergebnis, 3 richtige Tordifferenz, 2 richtige Tendenz, sonst 0.
    // Unentschieden: Tipp 1:1 bei 2:2 hat dieselbe Tordifferenz (0) und bekommt 3 Punkte.
    public static int points(int tipHome, int tipAway, int resultHome, int resultAway) {
        if (tipHome == resultHome && tipAway == resultAway) {
            return EXACT;
        }
        int tipDifference = tipHome - tipAway;
        int resultDifference = resultHome - resultAway;
        if (tipDifference == resultDifference) {
            return GOAL_DIFFERENCE;
        }
        if (Integer.signum(tipDifference) == Integer.signum(resultDifference)) {
            return TENDENCY;
        }
        return WRONG;
    }
}
