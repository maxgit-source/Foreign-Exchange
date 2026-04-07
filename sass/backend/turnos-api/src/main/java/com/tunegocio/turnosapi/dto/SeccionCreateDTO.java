package com.tunegocio.turnosapi.dto;

import com.tunegocio.turnosapi.entity.SeccionTipo;
import jakarta.validation.constraints.NotNull;

public record SeccionCreateDTO(
        @NotNull SeccionTipo tipo,
        int orden,
        boolean visible,
        String config,
        String animacionEntrada,
        Integer animacionDuracion,
        Integer animacionDelay,
        String estilos
) {}
