package com.tunegocio.turnosapi.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record SlotRecomendadoDTO(
        LocalDate fecha,
        LocalTime horaInicio,
        LocalTime horaFin,
        String    razon,
        double    score
) {}
