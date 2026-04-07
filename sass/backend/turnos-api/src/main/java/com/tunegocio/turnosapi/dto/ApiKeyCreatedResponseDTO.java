package com.tunegocio.turnosapi.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Respuesta de creación de API key — incluye el valor raw (solo se muestra una vez).
 */
public record ApiKeyCreatedResponseDTO(
        Long          id,
        String        nombre,
        String        apiKey,          // valor completo — mostrar solo en creación
        List<String>  permisos,
        LocalDateTime createdAt
) {}
