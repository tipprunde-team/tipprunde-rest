package com.mosbach.tipprunde.error;

import org.springframework.http.HttpStatus;

// KI-Log AH-02 "Übertrage den Prof-Login auf JWT im Header."
// Fachlicher Fehler mit HTTP-Status und Fehlercode
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}