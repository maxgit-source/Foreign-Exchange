package com.tunegocio.turnosapi.dto;

import com.tunegocio.turnosapi.entity.DiaSemana;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
public class DisponibilidadResponseDTO {

    private Long      id;
    private DiaSemana dia;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private boolean   activo;
}
