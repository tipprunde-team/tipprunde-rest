package com.mosbach.tipprunde.model.group;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

// Antwort auf GET /api/groups/{groupId}, inviteCode steht nur für Admins drin (sonst fehlt das Feld)
public record GroupDetails(
        long id,
        String name,
        @JsonInclude(JsonInclude.Include.NON_NULL) String inviteCode,
        List<Member> members) {
}
