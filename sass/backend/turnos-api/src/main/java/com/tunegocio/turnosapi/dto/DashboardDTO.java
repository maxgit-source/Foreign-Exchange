package com.tunegocio.turnosapi.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardDTO(
        // Turnos del período
        long            turnosHoy,
        long            turnosSemana,
        long            turnosMes,
        double          tasaOcupacion,
        double          tasaNoShow,
        double          tasaCancelacion,

        // Ingresos (basado en turnos COMPLETADO)
        BigDecimal      ingresosMes,
        BigDecimal      ingresosAnterior,
        double          variacionIngresos,

        // Clientes
        long            clientesNuevos,
        long            clientesTotales,

        // Rankings
        List<ServicioKpiDTO> serviciosPopulares,
        List<HoraOcupacionDTO> horasPico,

        // Próximos turnos del día
        List<TurnoResponseDTO> proximosTurnos
) {}
