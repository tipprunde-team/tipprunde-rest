package com.mosbach.tipprunde.model.match;

// Spielstatus, unabhängig von football-data.org
public enum MatchStatus {
    UPCOMING,
    LIVE,
    FINISHED,
    POSTPONED,
    CANCELLED;

    // Status von football-data.org auf unseren Status abbilden
    public static MatchStatus fromFootballData(String externalStatus) {
        if (externalStatus == null) {
            throw new IllegalArgumentException("Status fehlt");
        }
        return switch (externalStatus.trim().toUpperCase()) {
            case "SCHEDULED", "TIMED" -> UPCOMING;
            case "IN_PLAY", "PAUSED" -> LIVE;
            case "FINISHED" -> FINISHED;
            case "POSTPONED", "SUSPENDED" -> POSTPONED;
            case "CANCELLED" -> CANCELLED;
            default -> throw new IllegalArgumentException("Unbekannter Status: " + externalStatus);
        };
    }
}