package com.mosbach.tipprunde.model.tip;

import java.time.Instant;

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