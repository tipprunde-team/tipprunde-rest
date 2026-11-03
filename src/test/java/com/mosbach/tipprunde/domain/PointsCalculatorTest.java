package com.mosbach.tipprunde.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// KI-Log AH-16 "Berechne die Punkte der Tipps, sobald ein Spiel als beendet gemeldet wird."
// Alle Beispiele aus US-24 und dem API-Design (Kap. 5.7), dazu Unentschieden, Auswärtssieg und 0:0
class PointsCalculatorTest {

    @ParameterizedTest(name = "Ergebnis {2}:{3}, Tipp {0}:{1} ergibt {4} Punkte")
    @CsvSource({
            // Heimsieg 3:1 (Tabelle aus dem API-Design)
            "3, 1, 3, 1, 4",
            "2, 0, 3, 1, 3",
            "4, 0, 3, 1, 2",
            "1, 1, 3, 1, 0",
            "1, 3, 3, 1, 0",
            // Unentschieden 2:2: anderes Unentschieden hat dieselbe Tordifferenz 0
            "2, 2, 2, 2, 4",
            "1, 1, 2, 2, 3",
            "0, 0, 2, 2, 3",
            "2, 1, 2, 2, 0",
            // Auswärtssieg 0:2
            "0, 2, 0, 2, 4",
            "1, 3, 0, 2, 3",
            "0, 1, 0, 2, 2",
            "2, 0, 0, 2, 0",
            // torloses Unentschieden
            "0, 0, 0, 0, 4",
            "3, 3, 0, 0, 3",
            "1, 0, 0, 0, 0",
            // hoher Sieg
            "6, 1, 5, 0, 3",
            "1, 0, 5, 0, 2"
    })
    void pointsFollowTheScheme(int tipHome, int tipAway, int resultHome, int resultAway, int expected) {
        assertEquals(expected, PointsCalculator.points(tipHome, tipAway, resultHome, resultAway));
    }
}
