package com.tunegocio.turnosapi.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Estructura uniforme de respuesta de error para toda la API.
 * Garantiza que todos los errores tengan el mismo formato JSON.
 */
@Getter
@Builder
public class ErrorResponse {

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime timestamp;

    private final int status;

    private final String error;

    private final String message;

    private final String path;
}
