package com.mosbach.tipprunde.model.group;

// KI-Log MA-07 "Gruppen-Endpunkte mit zentraler Mitgliedschaftsprüfung und Tests nach Plan umsetzen."
// Antwort auf POST /api/groups, role ist für den Ersteller immer ADMIN
public record GroupCreated(long id, String name, String inviteCode, String role) {
}
