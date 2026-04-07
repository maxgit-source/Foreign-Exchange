package com.tunegocio.turnosapi.dto;

import java.util.List;

public record PaginaDTO(
        Long id,
        String slug,
        String titulo,
        String descripcion,
        boolean activa,
        boolean esHome,
        boolean enNav,
        int ordenNav,
        String seoTitulo,
        String seoDescripcion,
        String ogImagenUrl,
        List<SeccionDTO> secciones
) {}
