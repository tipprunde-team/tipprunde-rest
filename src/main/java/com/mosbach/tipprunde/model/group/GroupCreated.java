package com.mosbach.tipprunde.model.group;

// Antwort auf POST /api/groups, role ist für den Ersteller immer ADMIN
public record GroupCreated(long id, String name, String inviteCode, String role) {
}
