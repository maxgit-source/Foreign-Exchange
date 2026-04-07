package com.tunegocio.turnosapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

/**
 * Slot de tiempo libre en la agenda de un profesional.
 * Devuelto por el endpoint de disponibilidad para que el cliente
 * pueda seleccionar un horario al reservar un turno.
 */
@Getter
@Builder
public class SlotDisponibleDTO {

    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaInicio;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaFin;
}
