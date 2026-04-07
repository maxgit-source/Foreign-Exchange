package com.tunegocio.turnosapi.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ApiKeyResponseDTO(
        Long          id,
        String        nombre,
        String        keyPreview,      // primeros 8 chars + "..." — solo al crear
        List<String>  permisos,
        boolean       activo,
        LocalDateTime ultimoUso,
        LocalDateTime createdAt
) {}
