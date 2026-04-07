package com.tunegocio.turnosapi.dto;

public record PlantillaDTO(
        Long id,
        String codigo,
        String nombre,
        String descripcion,
        String categoria,
        String previewUrl,
        String thumbnailUrl,
        String tags,
        boolean activa,
        int orden,
        String configDefault
) {}
