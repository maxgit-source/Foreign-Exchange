package com.tunegocio.turnosapi.dto;

public record ServicioKpiDTO(
        Long    id,
        String  nombre,
        long    cantidadTurnos,
        double  porcentaje
) {}
