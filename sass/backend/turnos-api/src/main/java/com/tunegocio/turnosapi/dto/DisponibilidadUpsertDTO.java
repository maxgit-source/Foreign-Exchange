package com.tunegocio.turnosapi.dto;

import com.tunegocio.turnosapi.entity.DiaSemana;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * DTO para crear o reemplazar un bloque de disponibilidad de un profesional.
 * Se envía como lista: la operación PUT reemplaza toda la disponibilidad del profesional.
 */
@Getter
@Setter
public class DisponibilidadUpsertDTO {

    @NotNull(message = "El día de la semana es obligatorio")
    private DiaSemana dia;

    @NotNull(message = "La hora de inicio es obligatoria")
    private LocalTime horaInicio;

    @NotNull(message = "La hora de fin es obligatoria")
    private LocalTime horaFin;

    private boolean activo = true;
}
