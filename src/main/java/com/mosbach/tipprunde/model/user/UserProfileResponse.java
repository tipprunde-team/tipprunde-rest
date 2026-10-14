package com.mosbach.tipprunde.model.user;

import com.fasterxml.jackson.annotation.JsonProperty;

// Eigenes Profil, JSON-Feld heißt exakt isSystemAdmin
public record UserProfileResponse(
        long id,
        String email,
        String username,
        @JsonProperty("isSystemAdmin") boolean isSystemAdmin) {
}
