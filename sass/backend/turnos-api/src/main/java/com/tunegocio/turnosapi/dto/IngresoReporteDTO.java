package com.tunegocio.turnosapi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record IngresoReporteDTO(
        LocalDate   desde,
        LocalDate   hasta,
        BigDecimal  totalPeriodo,
        BigDecimal  totalPeriodoAnterior,
        double      variacionPorcentaje,
        List<IngresosPorDiaDTO> porDia
) {
    public record IngresosPorDiaDTO(
            LocalDate  fecha,
            BigDecimal total
    ) {}
}
