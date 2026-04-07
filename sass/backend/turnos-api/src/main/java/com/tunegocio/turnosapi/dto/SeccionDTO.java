package com.tunegocio.turnosapi.dto;

import com.tunegocio.turnosapi.entity.SeccionTipo;

public record SeccionDTO(
        Long id,
        Long paginaId,
        SeccionTipo tipo,
        int orden,
        boolean visible,
        String config,
        String animacionEntrada,
        Integer animacionDuracion,
        Integer animacionDelay,
        String estilos
) {}
