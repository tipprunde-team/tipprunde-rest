package com.mosbach.tipprunde.model.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Request-Body für POST /api/groups
public record CreateGroupRequest(
        @NotBlank(message = "Bitte einen Namen für die Tippgruppe angeben.")
        @Size(max = 50, message = "Der Name der Tippgruppe darf höchstens 50 Zeichen lang sein.")
        String name) {

    // Name vor der Prüfung trimmen, 1 bis 50 Zeichen zählen ohne Leerzeichen am Rand
    public CreateGroupRequest {
        name = name == null ? null : name.trim();
    }
}
