package com.mosbach.tipprunde.model.tip;

import java.time.Instant;

// Response of PUT .../tip: the stored tip
public record TipResponse(long matchId, int home, int away, Instant updatedAt) {
}
