package com.legalfam.backend.chat.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ChatAskRequest(
        @NotBlank(message = "Message is required")
        @Size(max = 4000, message = "Message must be at most 4000 characters")
        String message,

        @NotNull(message = "Session id is required")
        UUID sessionId,

        // Idioma en el que el usuario escribe y quiere leer. Ausente significa espanol, de
        // modo que los clientes anteriores a esta funcion siguen funcionando sin cambios.
        @Pattern(regexp = "es|qu|ay", message = "Language must be one of: es, qu, ay")
        String language
) {
}
