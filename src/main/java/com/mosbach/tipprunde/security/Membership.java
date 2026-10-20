package com.mosbach.tipprunde.security;

import java.time.Instant;

// Mitgliedschaft eines Nutzers in einer Gruppe, role ist ADMIN oder MEMBER
public record Membership(long groupId, long userId, String role, Instant joinedAt) {

    public static final String ADMIN = "ADMIN";
    public static final String MEMBER = "MEMBER";

    public boolean isAdmin() {
        return ADMIN.equals(role);
    }
}
