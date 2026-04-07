package com.tunegocio.turnosapi.dto;

import jakarta.validation.constraints.Size;

public record SitioConfigUpdateDTO(
        @Size(max = 100) String plantillaCodigo,
        @Size(max = 255) String dominioCustom,
        @Size(max = 255) String nombreSitio,
        String descripcionSitio,
        String faviconUrl,
        String logoUrl,
        String paleta,
        String redesSociales,
        @Size(max = 255) String seoTitulo,
        String seoDescripcion,
        String seoKeywords,
        String ogImagenUrl,
        @Size(max = 100) String fuentePrincipal,
        @Size(max = 100) String fuenteTitulos,
        Boolean animacionesActivas,
        Boolean modoOscuroDefault,
        Boolean moduloTienda,
        Boolean moduloBlog,
        Boolean moduloTurnos,
        Boolean sitioActivo,
        String mensajeMantenimiento,
        String configExtra
) {}
