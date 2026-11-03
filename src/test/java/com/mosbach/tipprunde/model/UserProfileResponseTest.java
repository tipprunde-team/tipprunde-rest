package com.mosbach.tipprunde.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mosbach.tipprunde.model.user.UserProfileResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// KI-Log MA-06 "Token zentral für alle geschützten Pfade prüfen, Nutzer-ID nur aus dem Token."
// Das JSON-Feld muss exakt isSystemAdmin heißen (Vertrag mit dem Frontend)
class UserProfileResponseTest {

    @Test
    void jsonFieldIsCalledIsSystemAdmin() {
        JsonMapper mapper = JsonMapper.builder().build();

        JsonNode json = mapper.valueToTree(new UserProfileResponse(7, "anna@example.de", "anna", true));

        assertTrue(json.has("isSystemAdmin"));
        assertEquals(true, json.get("isSystemAdmin").asBoolean());
        assertEquals(4, json.size());
    }
}
