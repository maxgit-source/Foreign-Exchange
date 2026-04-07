package com.tunegocio.turnosapi.dto;

import com.tunegocio.turnosapi.entity.BlogEstado;

import java.time.LocalDateTime;

public record BlogPostDTO(
        Long id,
        String titulo,
        String slug,
        String extracto,
        String contenido,
        String imagenPortada,
        BlogEstado estado,
        LocalDateTime publicadoAt,
        BlogCategoriaDTO categoria,
        String autorNombre,
        String seoTitulo,
        String seoDescripcion,
        String ogImagenUrl,
        String tags,
        long vistas,
        LocalDateTime createdAt
) {}
