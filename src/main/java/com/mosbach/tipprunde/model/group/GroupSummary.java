package com.mosbach.tipprunde.model.group;

// KI-Log MA-07 "Gruppen-Endpunkte mit zentraler Mitgliedschaftsprüfung und Tests nach Plan umsetzen."
// Eintrag in GET /api/groups: Gruppe aus Sicht des angemeldeten Nutzers
public record GroupSummary(long id, String name, String role, int memberCount, int myRank, int myPoints) {
}
