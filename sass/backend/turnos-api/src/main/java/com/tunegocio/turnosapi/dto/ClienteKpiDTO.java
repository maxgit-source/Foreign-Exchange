package com.tunegocio.turnosapi.dto;

public record ClienteKpiDTO(
        Long   id,
        String nombre,
        String email,
        long   cantidadTurnos
) {}
