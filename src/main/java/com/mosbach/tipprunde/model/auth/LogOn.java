package com.mosbach.tipprunde.model.auth;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Request-Body für POST /api/auth
public record LogOn(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password) {

    // E-Mail vor der Prüfung trimmen und klein schreiben
    public LogOn {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}