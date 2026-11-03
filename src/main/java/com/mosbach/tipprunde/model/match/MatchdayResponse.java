package com.mosbach.tipprunde.model.match;

import java.time.Instant;
import java.util.List;

// KI-Log AH-07 "Baue GET …/matches mit Sperre und Heroku-Setup."
// Response of GET /api/groups/{groupId}/matches; serverTime lets the client show the lock correctly
public record MatchdayResponse(int matchday, Instant serverTime, List<MatchEntry> matches) {
}
