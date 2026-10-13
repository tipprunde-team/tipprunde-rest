package com.mosbach.tipprunde.model.auth;

import java.time.Instant;

import com.mosbach.tipprunde.model.user.UserSummary;

// Antwort auf erfolgreichen Login
public record TokenAnswer(String token, Instant expiresAt, UserSummary user) {
}