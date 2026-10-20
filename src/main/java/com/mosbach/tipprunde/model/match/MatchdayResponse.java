package com.mosbach.tipprunde.model.match;

import java.time.Instant;
import java.util.List;

// Response of GET /api/groups/{groupId}/matches; serverTime lets the client show the lock correctly
public record MatchdayResponse(int matchday, Instant serverTime, List<MatchEntry> matches) {
}
