package com.tunegocio.turnosapi.dto;

import com.tunegocio.turnosapi.entity.BlogEstado;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlogPostCreateDTO(
        @NotBlank @Size(max = 500) String titulo,
        @NotBlank @Size(max = 500) String slug,
        String extracto,
        @NotBlank String contenido,
        String imagenPortada,
        BlogEstado estado,
        Long categoriaId,
        String autorNombre,
        @Size(max = 255) String seoTitulo,
        String seoDescripcion,
        String ogImagenUrl,
        String tags
) {}
