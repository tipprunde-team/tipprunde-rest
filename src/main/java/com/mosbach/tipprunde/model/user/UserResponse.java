package com.mosbach.tipprunde.model.user;

// KI-Log MA-05 "Registrierung mit Passwort-Hash, Fehlercodes und Tests umsetzen."
// Antwort auf erfolgreiche Registrierung (nie das Passwort oder den Hash)
public record UserResponse(long id, String email, String username) {
}
