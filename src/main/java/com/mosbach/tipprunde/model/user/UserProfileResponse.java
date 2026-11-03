package com.mosbach.tipprunde.model.user;

import com.fasterxml.jackson.annotation.JsonProperty;

// KI-Log MA-06 "Token zentral für alle geschützten Pfade prüfen, Nutzer-ID nur aus dem Token."
// Eigenes Profil, JSON-Feld heißt exakt isSystemAdmin
public record UserProfileResponse(
        long id,
        String email,
        String username,
        @JsonProperty("isSystemAdmin") boolean isSystemAdmin) {
}
