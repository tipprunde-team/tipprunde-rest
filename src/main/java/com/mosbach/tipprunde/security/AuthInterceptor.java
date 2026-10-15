package com.mosbach.tipprunde.security;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.mosbach.tipprunde.data.api.AuthManager;
import com.mosbach.tipprunde.error.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Prüft bei allen geschützten Endpunkten das Bearer-Token und legt die userId als Request-Attribut ab
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID_ATTRIBUTE = "userId";

    private static final String BEARER_PREFIX = "Bearer ";

    // Ohne Token erreichbar (NFA-03), die HTTP-Methode zählt: DELETE /api/auth ist geschützt
    private static final List<PublicEndpoint> PUBLIC_ENDPOINTS = List.of(
            new PublicEndpoint("POST", "/api/users"),
            new PublicEndpoint("POST", "/api/auth"),
            new PublicEndpoint("GET", "/api/ping"),
            new PublicEndpoint("GET", "/api/invites/**"));

    private final AuthManager authManager;

    public AuthInterceptor(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Nur Controller-Methoden prüfen: unbekannte Pfade bleiben 404, CORS-Preflight läuft durch
        if (!(handler instanceof HandlerMethod) || "OPTIONS".equals(request.getMethod()) || isPublic(request)) {
            return true;
        }
        String token = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        // Die Nutzer-ID kommt nur aus dem geprüften Token, nie aus Body oder URL (NFA-08)
        Long userId = authManager.getUserIdFromToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Bitte melde dich erneut an."));
        request.setAttribute(USER_ID_ATTRIBUTE, userId);
        return true;
    }

    // Pfad so lesen wie Spring beim Zuordnen des Controllers (normalisiert, ohne Pfadparameter)
    private static boolean isPublic(HttpServletRequest request) {
        String method = "HEAD".equals(request.getMethod()) ? "GET" : request.getMethod();
        var path = (ServletRequestPathUtils.hasParsedRequestPath(request)
                ? ServletRequestPathUtils.getParsedRequestPath(request)
                : ServletRequestPathUtils.parseAndCache(request)).pathWithinApplication();
        return PUBLIC_ENDPOINTS.stream().anyMatch(endpoint -> endpoint.matches(method, path));
    }

    // Token aus "Authorization: Bearer <token>", sonst null (Token in der URL zählt nicht)
    private static String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }

    private static final class PublicEndpoint {

        private final String method;
        private final PathPattern pattern;

        PublicEndpoint(String method, String pattern) {
            this.method = method;
            this.pattern = PathPatternParser.defaultInstance.parse(pattern);
        }

        boolean matches(String requestMethod, PathContainer path) {
            return method.equals(requestMethod.toUpperCase(Locale.ROOT)) && pattern.matches(path);
        }
    }
}
