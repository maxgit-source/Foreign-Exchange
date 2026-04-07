package com.tunegocio.turnosapi.dto;

public record SitioConfigDTO(
        Long id,
        String plantillaCodigo,
        String dominioCustom,
        String nombreSitio,
        String descripcionSitio,
        String faviconUrl,
        String logoUrl,
        String paleta,
        String redesSociales,
        String seoTitulo,
        String seoDescripcion,
        String seoKeywords,
        String ogImagenUrl,
        String fuentePrincipal,
        String fuenteTitulos,
        boolean animacionesActivas,
        boolean modoOscuroDefault,
        boolean moduloTienda,
        boolean moduloBlog,
        boolean moduloTurnos,
        boolean sitioActivo,
        String mensajeMantenimiento,
        String configExtra
) {}
