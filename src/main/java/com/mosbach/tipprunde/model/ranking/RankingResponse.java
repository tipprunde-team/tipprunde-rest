package com.mosbach.tipprunde.model.ranking;

import java.util.List;

// KI-Log MA-20 "Rangliste im Format des API-Designs mit Tests, passend zu web #52."
// Antwort auf GET /api/groups/{groupId}/ranking, matchday ist null bei der Gesamtrangliste
public record RankingResponse(long groupId, Integer matchday, List<RankingEntry> ranking) {
}
