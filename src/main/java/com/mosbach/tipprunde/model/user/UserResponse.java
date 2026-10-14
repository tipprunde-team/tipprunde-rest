package com.mosbach.tipprunde.model.user;

// Antwort auf erfolgreiche Registrierung (nie das Passwort oder den Hash)
public record UserResponse(long id, String email, String username) {
}
