package com.mosbach.tipprunde.model.group;

// Eintrag in GET /api/groups: Gruppe aus Sicht des angemeldeten Nutzers
public record GroupSummary(long id, String name, String role, int memberCount, int myRank, int myPoints) {
}
