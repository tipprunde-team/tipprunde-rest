package com.mosbach.tipprunde.model.group;

import jakarta.validation.constraints.NotBlank;

// KI-Log MA-08 "Beitritt per Code mit Obergrenze 200, sicher bei gleichzeitigen Beitritten."
// Request-Body für POST /api/groups/join
public record JoinGroupRequest(
        @NotBlank(message = "Bitte einen Einladungscode angeben.")
        String inviteCode) {

    // Leerzeichen am Rand (z. B. beim Einfügen aus einer Nachricht) stören nicht
    public JoinGroupRequest {
        inviteCode = inviteCode == null ? null : inviteCode.trim();
    }
}
