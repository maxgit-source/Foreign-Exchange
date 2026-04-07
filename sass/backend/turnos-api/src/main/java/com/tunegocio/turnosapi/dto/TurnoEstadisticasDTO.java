package com.tunegocio.turnosapi.dto;

import java.util.Map;

public record TurnoEstadisticasDTO(
        long              total,
        Map<String, Long> porEstado,
        double            tasaCompletados,
        double            tasaCancelacion,
        double            tasaNoShow,
        long              turnosHoy,
        long              turnosSemana,
        long              turnosMes
) {}
