package com.mosbach.tipprunde.model.group;

import java.time.Instant;

// Mitglied in GET /api/groups/{groupId}, role ist ADMIN oder MEMBER
public record Member(long userId, String username, String role, Instant joinedAt) {
}
