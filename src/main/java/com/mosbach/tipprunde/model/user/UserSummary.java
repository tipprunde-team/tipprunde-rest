package com.mosbach.tipprunde.model.user;

// Kurzinfo zum Nutzer, z. B. in der Login-Antwort
public record UserSummary(long id, String username) {
}