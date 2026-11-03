package com.mosbach.tipprunde.model.tip;

import java.time.Instant;

// KI-Log AH-08 "PUT …/tip mit Prüfreihenfolge und Tests."
// Response of PUT .../tip: the stored tip
public record TipResponse(long matchId, int home, int away, Instant updatedAt) {
}
