package com.mosbach.tipprunde.model.auth;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Request-Body für POST /api/auth
public record LogOn(
        @NotBlank(message = "Bitte eine E-Mail-Adresse angeben.")
        @Email(message = "Die E-Mail-Adresse ist ungültig.")
        @Size(max = 254, message = "Die E-Mail-Adresse darf höchstens 254 Zeichen lang sein.")
        String email,

        @NotBlank(message = "Bitte ein Passwort angeben.")
        @Size(max = 72, message = "Das Passwort darf höchstens 72 Zeichen lang sein.")
        String password) {

    // E-Mail vor der Prüfung trimmen und klein schreiben
    public LogOn {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}