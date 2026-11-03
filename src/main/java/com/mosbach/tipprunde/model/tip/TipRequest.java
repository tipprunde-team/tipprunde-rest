package com.mosbach.tipprunde.model.tip;

// KI-Log AH-08 "PUT …/tip mit Prüfreihenfolge und Tests."
// Body of PUT .../tip; Object instead of int so that 1.5, "2" and missing values can be rejected
public record TipRequest(Object home, Object away) {
}
