package com.mosbach.tipprunde.model.group;

// Antwort auf GET /api/invites/{inviteCode}: Vorschau vor dem Beitritt, ohne Mitgliederliste und ohne Code
public record InvitePreview(long groupId, String groupName, int memberCount) {
}
