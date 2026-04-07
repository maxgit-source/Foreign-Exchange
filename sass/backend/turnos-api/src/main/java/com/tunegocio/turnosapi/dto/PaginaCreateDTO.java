package com.tunegocio.turnosapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaginaCreateDTO(
        @NotBlank @Size(max = 200) String slug,
        @NotBlank @Size(max = 255) String titulo,
        String descripcion,
        boolean activa,
        boolean enNav,
        int ordenNav,
        @Size(max = 255) String seoTitulo,
        String seoDescripcion,
        String ogImagenUrl
) {}
