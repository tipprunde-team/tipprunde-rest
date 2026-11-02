package com.mosbach.tipprunde.model.ranking;

// Punkte eines Mitglieds vor der Rangvergabe (Rohdaten aus der Datenbank)
public record MemberScore(long userId, String username, int points, int exactTips) {
}
