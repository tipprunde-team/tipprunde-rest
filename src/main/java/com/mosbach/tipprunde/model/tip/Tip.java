package com.mosbach.tipprunde.model.tip;

import java.time.Instant;

// KI-Log AH-01 "Leite Modellklassen aus der API-Antwort ab."
// Tipp eines Nutzers in einer Gruppe, entspricht Tabelle tips
public record Tip(
        Long id,
        long groupId,
        long userId,
        long matchId,
        int homeGoals,
        int awayGoals,
        Integer points,             // null, bis das Spiel gewertet ist
        Instant updatedAt
) {
}