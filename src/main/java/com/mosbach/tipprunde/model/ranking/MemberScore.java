package com.mosbach.tipprunde.model.ranking;

// KI-Log MA-20 "Rangliste im Format des API-Designs mit Tests, passend zu web #52."
// Punkte eines Mitglieds vor der Rangvergabe (Rohdaten aus der Datenbank)
public record MemberScore(long userId, String username, int points, int exactTips) {
}
