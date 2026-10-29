package com.mosbach.tipprunde.messaging;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Event MATCH_UPDATED aus der Queue tipprunde.match-events (Vertrag aus dem Projektplan, Teil D.2)
@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchEvent(
        String type,
        Long externalId,
        Integer matchday,
        Instant kickoffUtc,
        String status,
        Team homeTeam,
        Team awayTeam,
        Score score,
        Instant occurredAt) {

    public static final String TYPE = "MATCH_UPDATED";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(String name, String shortName, String crest) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Score(Integer home, Integer away) {
    }
}
