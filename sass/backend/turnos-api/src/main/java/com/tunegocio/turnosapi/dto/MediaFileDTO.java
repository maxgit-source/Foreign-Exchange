package com.tunegocio.turnosapi.dto;

import java.time.LocalDateTime;

public record MediaFileDTO(
        Long id,
        String cloudinaryId,
        String url,
        String nombre,
        String tipo,
        String formato,
        Long tamanioBytes,
        Integer ancho,
        Integer alto,
        String carpeta,
        String tags,
        LocalDateTime createdAt
) {}
