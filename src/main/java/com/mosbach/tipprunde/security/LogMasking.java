package com.mosbach.tipprunde.security;

// KI-Log MA-13 "Logging für fehlgeschlagene Logins und abgelehnte Zugriffe ohne Geheimnisse bauen."
// Maskiert personenbezogene Werte, bevor sie in ein Log geschrieben werden (NFA-24)
public final class LogMasking {

    private LogMasking() {
    }

    // anna@example.de wird zu a***@example.de, ohne gültiges Format kommt nur *** heraus
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "(empty)";
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return trimmed.charAt(0) + "***" + trimmed.substring(at);
    }
}
