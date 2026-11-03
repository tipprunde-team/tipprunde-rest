package com.mosbach.tipprunde.error;

// KI-Log AH-02 "Übertrage den Prof-Login auf JWT im Header."
// Einheitliches Fehlerformat: {"error":{"code":"...","message":"..."}}
public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message) {
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorBody(code, message));
    }
}