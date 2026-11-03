package com.mosbach.tipprunde.model.ranking;

// KI-Log MA-20 "Rangliste im Format des API-Designs mit Tests, passend zu web #52."
// Eine Zeile der Rangliste: Rang nach Punkten, exactTips = Anzahl der Tipps mit voller Punktzahl
public record RankingEntry(int rank, long userId, String username, int points, int exactTips) {
}
