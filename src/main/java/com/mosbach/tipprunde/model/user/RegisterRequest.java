package com.mosbach.tipprunde.model.user;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Request-Body für POST /api/users
public record RegisterRequest(
        @NotBlank(message = "Bitte eine E-Mail-Adresse angeben.")
        @Email(message = "Die E-Mail-Adresse ist ungültig.")
        @Size(max = 254, message = "Die E-Mail-Adresse darf höchstens 254 Zeichen lang sein.")
        String email,

        @NotBlank(message = "Bitte einen Benutzernamen angeben.")
        @Pattern(regexp = "[A-Za-z0-9_.-]{3,30}",
                message = "Der Benutzername muss 3 bis 30 Zeichen lang sein und darf nur Buchstaben, Ziffern, _, . und - enthalten.")
        String username,

        @NotBlank(message = "Bitte ein Passwort angeben.")
        @Size(min = 8, max = 72, message = "Das Passwort muss 8 bis 72 Zeichen lang sein.")
        String password) {

    // E-Mail vor der Prüfung trimmen und klein schreiben
    public RegisterRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
