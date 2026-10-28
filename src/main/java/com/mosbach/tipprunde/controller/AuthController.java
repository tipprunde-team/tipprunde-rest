package com.mosbach.tipprunde.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mosbach.tipprunde.data.api.AuthManager;
import com.mosbach.tipprunde.error.ApiException;
import com.mosbach.tipprunde.model.auth.LogOn;
import com.mosbach.tipprunde.model.auth.TokenAnswer;

import jakarta.validation.Valid;

// Login (POST) und Logout (DELETE) unter /api/auth
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthManager authManager;

    public AuthController(AuthManager authManager) {
        this.authManager = authManager;
    }

    @PostMapping
    public TokenAnswer logOn(@Valid @RequestBody LogOn logOn) {
        // Erfolg und Fehlschlag protokolliert der Manager (E-Mail nur maskiert, nie Passwort oder Token)
        return authManager.logOn(logOn.email(), logOn.password());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logOff(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = extractBearerToken(authorization);
        Long userId = authManager.getUserIdFromToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Nicht angemeldet oder Sitzung abgelaufen."));
        log.info("Logout: userId={}", userId);
        authManager.logOff(token);
    }

    // Token aus "Authorization: Bearer <token>", sonst null
    private static String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }
}