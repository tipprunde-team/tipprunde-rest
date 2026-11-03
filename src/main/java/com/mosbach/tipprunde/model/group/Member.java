package com.mosbach.tipprunde.model.group;

import java.time.Instant;

// KI-Log MA-07 "Gruppen-Endpunkte mit zentraler Mitgliedschaftsprüfung und Tests nach Plan umsetzen."
// Mitglied in GET /api/groups/{groupId}, role ist ADMIN oder MEMBER
public record Member(long userId, String username, String role, Instant joinedAt) {
}
