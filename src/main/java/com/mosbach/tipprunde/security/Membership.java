package com.mosbach.tipprunde.security;

import java.time.Instant;

// KI-Log MA-07 "Gruppen-Endpunkte mit zentraler Mitgliedschaftsprüfung und Tests nach Plan umsetzen."
// Mitgliedschaft eines Nutzers in einer Gruppe, role ist ADMIN oder MEMBER
public record Membership(long groupId, long userId, String role, Instant joinedAt) {

    public static final String ADMIN = "ADMIN";
    public static final String MEMBER = "MEMBER";

    public boolean isAdmin() {
        return ADMIN.equals(role);
    }
}
