package com.mosbach.tipprunde.data.api;

import java.util.Optional;

import com.mosbach.tipprunde.model.auth.TokenAnswer;

// Login, Logout und Token-Prüfung
public interface AuthManager {

    // 401 UNAUTHORIZED bei falscher E-Mail oder falschem Passwort
    TokenAnswer logOn(String email, String password);

    // Zustandslos: Client verwirft das Token
    void logOff(String token);

    Optional<Long> getUserIdFromToken(String token);
}