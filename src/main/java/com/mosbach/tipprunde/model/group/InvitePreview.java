package com.mosbach.tipprunde.model.group;

// KI-Log MA-08 "Beitritt per Code mit Obergrenze 200, sicher bei gleichzeitigen Beitritten."
// Antwort auf GET /api/invites/{inviteCode}: Vorschau vor dem Beitritt, ohne Mitgliederliste und ohne Code
public record InvitePreview(long groupId, String groupName, int memberCount) {
}
