package com.mosbach.tipprunde.model.ranking;

import java.util.List;

// Antwort auf GET /api/groups/{groupId}/ranking, matchday ist null bei der Gesamtrangliste
public record RankingResponse(long groupId, Integer matchday, List<RankingEntry> ranking) {
}
